package tontine.service

import zio.*
import java.time.Instant
import tontine.*

/**
 * Manages the full lifecycle of a missed-contribution dispute inside a
 * Njangi / tontine circle:
 *
 *   1. **Raise**       — any circle admin can open a dispute for a missed contribution.
 *   2. **Notify**      — the member is notified (via MoMo or audit log) and given a grace period.
 *   3. **Arbitrate**   — if unresolved, escalate to circle arbitrators.
 *   4. **Resolve**     — apply a `Penalty`, `Excuse`, or `Expel` the member.
 *
 * Every state transition is appended to the `AuditRepo` so the full history
 * feeds the member's portable tontine credit credential (see `ScoreService`).
 */
trait DisputeService {
  def raiseDispute(req: RaiseDisputeReq): IO[AppError, Dispute]
  def notifyMember(disputeId: DisputeId): IO[AppError, Dispute]
  def escalateToArbitration(disputeId: DisputeId): IO[AppError, Dispute]
  def resolveDispute(
      disputeId: DisputeId,
      req: ResolveDisputeReq
  ): IO[AppError, Dispute]
  def listByCircle(circleId: CircleId): UIO[List[Dispute]]
  def listByMember(memberId: MemberId): UIO[List[Dispute]]
  def penaltiesByMember(memberId: MemberId): UIO[List[Penalty]]
}

object DisputeService {

  /** Default penalty rate: 10 % of the missed contribution amount. */
  private val PenaltyRate: Double = 0.10

  val live: ZLayer[
    DisputeRepo & ContributionRepo & CircleRepo & AuditRepo,
    Nothing,
    DisputeService
  ] =
    ZLayer.fromFunction { (
        repo: DisputeRepo,
        contribs: ContributionRepo,
        circles: CircleRepo,
        audit: AuditRepo
    ) =>
      new DisputeService {

        override def raiseDispute(req: RaiseDisputeReq): IO[AppError, Dispute] =
          for {
            // Validate contribution belongs to the stated circle and member
            c <- contribs.get(req.contributionId).mapError(identity)
            _ <- ZIO
              .fail(AppError.Validation("Contribution does not belong to stated circle"))
              .when(c.circleId != req.circleId)
            _ <- ZIO
              .fail(AppError.Validation("Contribution does not belong to stated member"))
              .when(c.memberId != req.memberId)
            _ <- ZIO
              .fail(
                AppError.Validation(
                  "Cannot raise dispute: contribution is already Paid"
                )
              )
              .when(c.status == ContributionStatus.Paid)

            id  <- DisputeId.random
            now <- Clock.instant
            dispute = Dispute(
              id = id,
              circleId = req.circleId,
              memberId = req.memberId,
              contributionId = req.contributionId,
              reason = req.reason,
              status = DisputeStatus.Raised,
              raisedAt = now,
              resolvedAt = None,
              resolutionNote = None
            )
            _ <- repo.create(dispute)
            _ <- audit.append(
              s"dispute.raised id=${id.value} circle=${req.circleId.value} " +
                s"member=${req.memberId.value} reason=${req.reason}"
            )
          } yield dispute

        override def notifyMember(disputeId: DisputeId): IO[AppError, Dispute] =
          for {
            d <- repo.get(disputeId).mapError(identity)
            _ <- ZIO
              .fail(AppError.Validation(s"Dispute is already ${d.status}; cannot notify"))
              .when(d.status != DisputeStatus.Raised)
            updated = d.copy(status = DisputeStatus.Notified)
            _ <- repo.update(updated).mapError(identity)
            _ <- audit.append(
              s"dispute.notified id=${disputeId.value} member=${d.memberId.value}"
            )
          } yield updated

        override def escalateToArbitration(
            disputeId: DisputeId
        ): IO[AppError, Dispute] =
          for {
            d <- repo.get(disputeId).mapError(identity)
            _ <- ZIO
              .fail(
                AppError.Validation(
                  s"Dispute must be in Notified state to escalate; current=${d.status}"
                )
              )
              .when(d.status != DisputeStatus.Notified)
            updated = d.copy(status = DisputeStatus.Arbitration)
            _ <- repo.update(updated).mapError(identity)
            _ <- audit.append(
              s"dispute.escalated id=${disputeId.value} member=${d.memberId.value}"
            )
          } yield updated

        override def resolveDispute(
            disputeId: DisputeId,
            req: ResolveDisputeReq
        ): IO[AppError, Dispute] =
          for {
            d <- repo.get(disputeId).mapError(identity)
            _ <- ZIO
              .fail(AppError.Validation("Dispute is not in a resolvable state"))
              .when(
                d.status == DisputeStatus.PenaltyApplied ||
                  d.status == DisputeStatus.Excused ||
                  d.status == DisputeStatus.Expelled
              )
            _ <- ZIO
              .fail(AppError.Validation("Invalid outcome for resolve"))
              .when(
                req.outcome != DisputeStatus.PenaltyApplied &&
                  req.outcome != DisputeStatus.Excused &&
                  req.outcome != DisputeStatus.Expelled
              )

            now <- Clock.instant
            // Apply penalty if outcome is PenaltyApplied
            _ <- req.outcome match {
              case DisputeStatus.PenaltyApplied =>
                for {
                  contrib <- contribs.get(d.contributionId).mapError(identity)
                  penaltyAmt = req.penaltyAmount.getOrElse(
                    Money(
                      amount = contrib.amount.amount * PenaltyRate,
                      currency = contrib.amount.currency
                    )
                  )
                  pid <- PenaltyId.random
                  penalty = Penalty(
                    id = pid,
                    disputeId = d.id,
                    memberId = d.memberId,
                    circleId = d.circleId,
                    amount = penaltyAmt,
                    reason = req.resolutionNote.getOrElse("Missed contribution penalty"),
                    issuedAt = now,
                    collected = false
                  )
                  _ <- repo.createPenalty(penalty)
                  _ <- audit.append(
                    s"dispute.penalty_issued id=${pid.value} dispute=${disputeId.value} " +
                      s"member=${d.memberId.value} amount=${penaltyAmt.amount} ${penaltyAmt.currency}"
                  )
                } yield ()
              case _ => ZIO.unit
            }

            resolved = d.copy(
              status = req.outcome,
              resolvedAt = Some(now),
              resolutionNote = req.resolutionNote
            )
            _ <- repo.update(resolved).mapError(identity)
            _ <- audit.append(
              s"dispute.resolved id=${disputeId.value} outcome=${req.outcome} " +
                s"member=${d.memberId.value}"
            )
          } yield resolved

        override def listByCircle(circleId: CircleId): UIO[List[Dispute]] =
          repo.byCircle(circleId)

        override def listByMember(memberId: MemberId): UIO[List[Dispute]] =
          repo.byMember(memberId)

        override def penaltiesByMember(memberId: MemberId): UIO[List[Penalty]] =
          repo.penaltiesByMember(memberId)
      }
    }
}
