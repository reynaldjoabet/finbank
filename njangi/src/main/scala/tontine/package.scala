package tontine
import zio.*
import zio.json.*
import java.time.*
import java.util.UUID
import java.time.Instant
import java.util.UUID
import zio.*
import zio.json.*
import java.time.LocalDate

enum PaymentStatus {
  case Succeeded, Pending, Failed
}

final case class BankTransaction(
    id: BankTxnId,
    reference: String,
    amount: Money,
    bookedAt: Instant
)

// ---------- JSON helpers ----------
given JsonEncoder[Instant] = JsonEncoder[String].contramap(_.toString)
given JsonDecoder[Instant] = JsonDecoder[String].map(Instant.parse)

given JsonEncoder[LocalDate] = JsonEncoder[String].contramap(_.toString)
given JsonDecoder[LocalDate] = JsonDecoder[String].map(LocalDate.parse)

// ---------- Opaque IDs ----------
opaque type CircleId = UUID
object CircleId {
  def fromUUID(uuid: UUID): CircleId = uuid
  def fromString(s: String): Option[CircleId] =
    scala.util.Try(UUID.fromString(s)).toOption
  def random: UIO[CircleId] = Random.nextUUID
  extension (id: CircleId) def value: UUID = id

  given JsonEncoder[CircleId] = JsonEncoder.uuid.contramap[CircleId](_.value)
  given JsonDecoder[CircleId] = JsonDecoder.uuid.map(fromUUID)
//   given JsonEncoder[CircleId] = JsonEncoder[UUID].contramap[CircleId](fromUUID)
//   given JsonDecoder[CircleId] = JsonDecoder[UUID].map(identity)
}
opaque type MemberId = UUID
object MemberId {

  def fromUUID(uuid: UUID): MemberId = uuid
  def fromString(s: String): Option[MemberId] =
    scala.util.Try(UUID.fromString(s)).toOption
  def random: UIO[MemberId] = Random.nextUUID
  extension (id: MemberId) def value: UUID = id

  given JsonEncoder[MemberId] = JsonEncoder.uuid.contramap[MemberId](_.value)
  given JsonDecoder[MemberId] = JsonDecoder.uuid
}
opaque type ContributionId = UUID
object ContributionId {
  def fromUUID(uuid: UUID): ContributionId = uuid
  def random: UIO[ContributionId] = Random.nextUUID
  extension (id: ContributionId) def value: UUID = id
  given JsonEncoder[ContributionId] =
    JsonEncoder.uuid.contramap[ContributionId](_.value)
  given JsonDecoder[ContributionId] = JsonDecoder.uuid
}

opaque type MobileMoneyTxId = String
object MobileMoneyTxId {
  def apply(s: String): MobileMoneyTxId = s
  extension (id: MobileMoneyTxId) def value: String = id
  given JsonEncoder[MobileMoneyTxId] =
    JsonEncoder.string.contramap[MobileMoneyTxId](_.value)
  given JsonDecoder[MobileMoneyTxId] = JsonDecoder.string
}
opaque type BankTxnId = String
object BankTxnId {
  def apply(s: String): BankTxnId = s
  extension (id: BankTxnId) def value: String = id
  given JsonEncoder[BankTxnId] =
    JsonEncoder.string.contramap[BankTxnId](_.value)
  given JsonDecoder[BankTxnId] = JsonDecoder.string
}
// ---------- Money ----------
final case class Money(amount: BigDecimal, currency: String = "XAF")
object Money {
  given JsonCodec[Money] = DeriveJsonCodec.gen[Money]
}
// ---------- Entities ----------
final case class Member(
    id: MemberId,
    fullName: String,
    phoneE164: String,
    phoneNumber: String = ""
)
object Member {
  given JsonCodec[Member] = DeriveJsonCodec.gen[Member]
}
final case class Circle(
    id: CircleId,
    name: String,
    createdAt: Instant,
    members: Set[MemberId],
    bankAccountRef: String // external bank account identifier (open banking)
)
object Circle {
  given JsonCodec[Circle] = DeriveJsonCodec.gen[Circle]
}
enum ContributionStatus derives JsonCodec {
  case Pending, Paid, Failed
}
final case class Contribution(
    id: ContributionId,
    circleId: CircleId,
    memberId: MemberId,
    amount: Money,
    dueDate: LocalDate,
    createdAt: Instant,
    status: ContributionStatus,
    paidAt: Option[Instant],
    mobileMoneyTxId: Option[MobileMoneyTxId],
    bankReconciled: Boolean,
    bankTxnId: Option[BankTxnId]
)

object Contribution {
  given JsonCodec[Contribution] = DeriveJsonCodec.gen[Contribution]
}
// ---------- Score ----------
final case class TontineScore(
    memberId: MemberId,
    totalDue: Int,
    paidOnTimeVerified: Int,
    paidLateVerified: Int,
    missed: Int,
    onTimeRate: Double, // 0.0 - 1.0
    score: Int, // 0 - 100 (simple mapping for MVP)
    updatedAt: Instant
)
object TontineScore {
  given JsonCodec[TontineScore] = DeriveJsonCodec.gen[TontineScore]
}

// sealed trait AppError extends Throwable
// object AppError {
//   final case class NotFound(msg: String) extends Exception(msg) with AppError
//   final case class Validation(msg: String) extends Exception(msg) with AppError
//   final case class Payment(msg: String) extends Exception(msg) with AppError
//   final case class Bank(msg: String) extends Exception(msg) with AppError
// }

final case class CreateCircleReq(name: String, bankAccountRef: String)
object CreateCircleReq {
  given JsonCodec[CreateCircleReq] = DeriveJsonCodec.gen[CreateCircleReq]
}
final case class CreateMemberReq(fullName: String, phoneE164: String)
object CreateMemberReq {
  given JsonCodec[CreateMemberReq] = DeriveJsonCodec.gen[CreateMemberReq]
}
final case class JoinCircleReq(memberId: MemberId)
object JoinCircleReq {
  given JsonCodec[JoinCircleReq] = DeriveJsonCodec.gen[JoinCircleReq]
}
final case class StartContributionReq(
    memberId: MemberId,
    amount: Money,
    dueDate: LocalDate
)
object StartContributionReq {
  given JsonCodec[StartContributionReq] =
    DeriveJsonCodec.gen[StartContributionReq]
}

// The persistent group of people
case class TontineCircle(
    id: UUID,
    name: String,
    contributionAmount: BigDecimal,
    members: List[Member] // The 10 people in the group
)

// The specific iteration of payments
case class PaymentCycle(
    id: UUID,
    circleId: UUID,
    roundNumber: Int, // e.g., Round 1 of 10
    winnerId: UUID, // The person receiving the pot this round
    status: "Pending" | "Collecting" | "Disbursed"
)
