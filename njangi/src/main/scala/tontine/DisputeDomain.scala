package tontine

import zio.json.*
import java.time.Instant
import java.util.UUID

// ── Opaque IDs ─────────────────────────────────────────────────────────────

opaque type DisputeId = UUID
object DisputeId {
  def apply(uuid: UUID): DisputeId = uuid
  def unapply(id: DisputeId): UUID = id
  def random: zio.UIO[DisputeId] = zio.Random.nextUUID

  extension (id: DisputeId) def value: UUID = id

  given CanEqual[DisputeId, DisputeId] = CanEqual.derived
  given JsonEncoder[DisputeId] = JsonEncoder.uuid
  given JsonDecoder[DisputeId] = JsonDecoder.uuid
}

opaque type PenaltyId = UUID
object PenaltyId {
  def apply(uuid: UUID): PenaltyId = uuid
  def unapply(id: PenaltyId): UUID = id
  def random: zio.UIO[PenaltyId] = zio.Random.nextUUID

  extension (id: PenaltyId) def value: UUID = id

  given CanEqual[PenaltyId, PenaltyId] = CanEqual.derived
  given JsonEncoder[PenaltyId] = JsonEncoder.uuid
  given JsonDecoder[PenaltyId] = JsonDecoder.uuid
}

// ── Enums ───────────────────────────────────────────────────────────────────

/**
 * Lifecycle of a missed-contribution dispute.
 *
 *   Raised → Notified → Arbitration → Resolved (Penalty | Excused | Expelled)
 */
enum DisputeStatus derives JsonCodec, CanEqual {
  /** Dispute raised, awaiting member response. */
  case Raised
  /** Notification sent; member has not responded within grace period. */
  case Notified
  /** Escalated to circle arbitrators. */
  case Arbitration
  /** Penalty applied; member remains in circle. */
  case PenaltyApplied
  /** Dispute excused (illness, emergency, etc.). */
  case Excused
  /** Member expelled from circle. */
  case Expelled
}

enum DisputeReason derives JsonCodec, CanEqual {
  case MissedContribution
  case LatePayout          // member received pot but delays contributing afterward
  case FraudSuspicion
  case InsufficientBalance
}

// ── Entities ────────────────────────────────────────────────────────────────

/**
 * A dispute raised against a circle member due to a missed or late
 * contribution.  The lifecycle drives notifications, arbitration, and
 * eventual penalty or expulsion — all recorded for the member's tontine
 * credit history.
 */
final case class Dispute(
    id: DisputeId,
    circleId: CircleId,
    memberId: MemberId,
    contributionId: ContributionId,
    reason: DisputeReason,
    status: DisputeStatus,
    raisedAt: Instant,
    resolvedAt: Option[Instant],
    resolutionNote: Option[String]
)
object Dispute {
  given JsonCodec[Dispute] = DeriveJsonCodec.gen[Dispute]
}

/**
 * Financial penalty recorded against a member after arbitration.
 * The `amountMinor` is deducted from the member's next payout or collected
 * via mobile money.
 */
final case class Penalty(
    id: PenaltyId,
    disputeId: DisputeId,
    memberId: MemberId,
    circleId: CircleId,
    amount: Money,
    reason: String,
    issuedAt: Instant,
    collected: Boolean
)
object Penalty {
  given JsonCodec[Penalty] = DeriveJsonCodec.gen[Penalty]
}

// ── Request / response DTOs ─────────────────────────────────────────────────

final case class RaiseDisputeReq(
    circleId: CircleId,
    memberId: MemberId,
    contributionId: ContributionId,
    reason: DisputeReason
)
object RaiseDisputeReq {
  given JsonCodec[RaiseDisputeReq] = DeriveJsonCodec.gen[RaiseDisputeReq]
}

final case class ResolveDisputeReq(
    outcome: DisputeStatus, // PenaltyApplied | Excused | Expelled
    penaltyAmount: Option[Money],
    resolutionNote: Option[String]
)
object ResolveDisputeReq {
  given JsonCodec[ResolveDisputeReq] = DeriveJsonCodec.gen[ResolveDisputeReq]
}
