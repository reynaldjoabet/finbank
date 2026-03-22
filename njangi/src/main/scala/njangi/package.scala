import java.util.UUID
import java.time.Instant

package object njangi {

  // Domain Identifiers
  opaque type UserId = UUID
  object UserId {
    def apply(uuid: UUID): UserId = uuid
    def unapply(id: UserId): UUID = id
    def random: UserId = UUID.randomUUID()

    given CanEqual[UserId, UserId] = CanEqual.derived
  }
  opaque type CircleId = UUID
  object CircleId {
    def apply(uuid: UUID): CircleId = uuid
    def unapply(id: CircleId): UUID = id

    given CanEqual[CircleId, CircleId] = CanEqual.derived
  }

  // Enums with parameters for Region-Specific logic
  enum Currency(val symbol: String) derives CanEqual {
    case XAF extends Currency("FCFA")
    case NGN extends Currency("₦")
    case USDC extends Currency("$")
  }
  case class User(
      id: UserId,
      fapiSubjectId: String,
      kycLevel: Int,
      globalTrustScore: Int,
      preferredCurrency: Currency
  )

  sealed trait SettlementCapability

  enum SettlementRail(val latencyMillis: Long) extends SettlementCapability
      derives CanEqual {
    case Gimac extends SettlementRail(2000)
    case Papss extends SettlementRail(5000)
    case StellarUsdc extends SettlementRail(500)
    case Internal extends SettlementRail(50)
  }
  enum PaymentMethod derives CanEqual {
    case MobileMoney(provider: String) // e.g., "Orange", "MTN"
    case BankTransfer(bankCode: String)
    case CryptoWallet(chain: String)
  }
  case class Transaction(
      id: UUID,
      userId: UserId,
      amount: BigDecimal,
      method: PaymentMethod,
      rail: SettlementRail,
      idempotencyKey: UUID,
      createdAt: Instant = Instant.now()
  )

  // Extending the domain with business logic
  extension (circle: NjangiCircle) {
    def isHighValue: Boolean =
      circle.contributionAmount > 500000 && circle.currency == Currency.XAF

    def generateVaultAddress(userId: UserId): String =
      s"vault-${circle.id.toString.take(8)}-${userId.toString.take(8)}"
  }
  case class Bid(userId: UserId, discount: Double)
  case class AuctionResult(winner: UserId, finalPot: BigDecimal)
  case class NoBids(reason: String)

  type PayoutOutcome = AuctionResult | NoBids

  object AuctionEngine {
    def resolve(bids: List[Bid], pot: BigDecimal): PayoutOutcome =
      bids.maxByOption(_.discount) match {
        case Some(winningBid) =>
          AuctionResult(winningBid.userId, pot * (1 - winningBid.discount))
        case None =>
          NoBids("No participants in this rotation cycle")
      }
  }

  // The current state of a member's participation
  // sealed trait ParticipationStatus
  // case object Active extends ParticipationStatus
  // case object Defaulted extends ParticipationStatus
  // case object Completed extends ParticipationStatus

  enum ParticipationStatus derives CanEqual {
    case Active, Defaulted, Completed
  }

  case class Member(
      id: UUID,
      name: String,
      momoNumber: String, // Orange/MTN Cameroon
      socialTrustScore: Int, // Calculated based on past performance
      status: ParticipationStatus
  )

  case class TontineCircle(
      id: UUID,
      name: String,
      contributionAmount: BigDecimal,
      currency: Currency,
      frequencyDays: Int,
      members: List[Member],
      payoutOrder: List[UUID], // The "Turn" order
      totalPot: BigDecimal
  )
  case class NjangiCircle(
      id: UUID,
      name: String,
      contributionAmount: BigDecimal,
      currency: Currency,
      frequencyDays: Int,
      members: List[Member],
      payoutOrder: List[UUID], // The "Turn" order
      totalPot: BigDecimal
  )

  enum PaymentStatus derives CanEqual {
    case Success, Failure
  }

  enum CircleState derives CanEqual {
    case Active, Completed, Defaulted
  }

}
