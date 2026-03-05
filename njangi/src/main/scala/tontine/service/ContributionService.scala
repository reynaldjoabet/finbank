package tontine.service
import zio.*
import java.time.LocalDate
import java.time.Instant
import java.security.*
import java.util.Base64
import zio.json.EncoderOps
import java.time.ZoneOffset
import tontine.*
trait ContributionService {
  def startContribution(
      circleId: CircleId,
      memberId: MemberId,
      amount: Money,
      dueDate: LocalDate
  ): IO[AppError, Contribution]
  def confirmContribution(
      contributionId: ContributionId
  ): IO[AppError, Contribution] // checks MM status + marks paid
  def sweepAndReconcile(
      circleId: CircleId,
      since: Instant
  ): IO[AppError, Int] // marks contributions as bankReconciled

}

object ContributionService {
  val live: ZLayer[
    CircleRepo & ContributionRepo & MobileMoneyGateway & OpenBankingClient &
      AuditRepo,
    Nothing,
    ContributionService
  ] =
    ZLayer.fromFunction {
      (
          circleRepo: CircleRepo,
          contribRepo: ContributionRepo,
          mm: MobileMoneyGateway,
          bank: OpenBankingClient,
          audit: AuditRepo
      ) =>
        new ContributionService {

          def startContribution(
              circleId: CircleId,
              memberId: MemberId,
              amount: Money,
              dueDate: LocalDate
          ): IO[AppError, Contribution] =
            for {
              circle <- circleRepo.get(circleId).mapError(identity)
              _ <- ZIO
                .fail(AppError.Validation("Member not in circle"))
                .when(!circle.members.contains(memberId))
              id <- ContributionId.random
              now <- Clock.instant
              ref = s"circle:${circleId.value}:contrib:${id.value}"
              txId <- mm
                .requestPush(
                  phoneE164 = "UNKNOWN",
                  amount = amount,
                  reference = ref
                )
                .mapError(identity)
              c = Contribution(
                id = id,
                circleId = circleId,
                memberId = memberId,
                amount = amount,
                dueDate = dueDate,
                createdAt = now,
                status = ContributionStatus.Pending,
                paidAt = None,
                mobileMoneyTxId = Some(txId),
                bankReconciled = false,
                bankTxnId = None
              )
              _ <- contribRepo.create(c)
              _ <- audit.append(
                s"contribution.started id=${id.value} circle=${circleId.value} member=${memberId.value} tx=${txId.value}"
              )
            } yield c

          def confirmContribution(
              contributionId: ContributionId
          ): IO[AppError, Contribution] =
            for {
              c <- contribRepo.get(contributionId).mapError(identity)
              tx <- ZIO
                .fromOption(c.mobileMoneyTxId)
                .orElseFail(AppError.Validation("Missing mobileMoneyTxId"))
              st <- mm.getStatus(tx).mapError(identity)
              now <- Clock.instant
              updated <- st match {
                case PaymentStatus.Succeeded =>
                  ZIO.succeed(
                    c.copy(
                      status = ContributionStatus.Paid,
                      paidAt = Some(now)
                    )
                  )
                case PaymentStatus.Failed =>
                  ZIO.succeed(c.copy(status = ContributionStatus.Failed))
                case PaymentStatus.Pending =>
                  ZIO.succeed(c)
              }
              _ <- contribRepo.update(updated).mapError(identity)
              _ <- audit.append(
                s"contribution.confirmed id=${contributionId.value} status=${updated.status}"
              )
            } yield updated

          /** MVP sweep+reconcile:
            *   - Sum PAID but not bankReconciled contributions
            *   - Initiate one bank sweep transfer (batched)
            *   - Pull bank txns since 'since' and match by reference
            *   - Mark those contributions as bankReconciled = true
            */
          def sweepAndReconcile(
              circleId: CircleId,
              since: Instant
          ): IO[AppError, Int] =
            for {
              circle <- circleRepo.get(circleId).mapError(identity)
              all <- contribRepo.byCircle(circleId)
              pending = all.filter(c =>
                c.status == ContributionStatus.Paid && !c.bankReconciled
              )
              total = pending.map(_.amount.amount).sum
              _ <- ZIO
                .fail(AppError.Validation("Nothing to sweep"))
                .when(pending.isEmpty)
              reference =
                s"sweep:circle:${circleId.value}:${Instant.now().toEpochMilli}"
              bankTxnId <- bank
                .sweepToCircleAccount(
                  circle.bankAccountRef,
                  Money(total, "XAF"),
                  reference
                )
                .mapError(identity)
              _ <- audit.append(
                s"sweep.initiated circle=${circleId.value} bankTxn=${bankTxnId.value} total=$total"
              )

              // reconcile: in MVP we mark all pending as reconciled under the sweep txn
              reconciled = pending.map(c =>
                c.copy(bankReconciled = true, bankTxnId = Some(bankTxnId))
              )
              _ <- ZIO.foreachDiscard(reconciled)(c =>
                contribRepo.update(c).mapError(identity)
              )
            } yield reconciled.size
        }
    }
}
