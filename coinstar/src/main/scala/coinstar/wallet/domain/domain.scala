package coinstar.wallet
import zio.json.*
import java.time.Instant
import java.util.UUID
import io.getquill.*
package object domain {
  given CanEqual[UUID, UUID] = CanEqual.derived

  opaque type UserId = UUID
  object UserId {
    def apply(uuid: UUID): UserId = uuid
    def unapply(id: UserId): UUID = id
    def random: UserId = UserId(UUID.randomUUID())
    def fromString(s: String): Either[String, UserId] =
      try Right(UserId(UUID.fromString(s)))
      catch {
        case e: IllegalArgumentException =>
          Left(s"Invalid UUID in subject claim: ${e.getMessage}")
      }

    extension (id: UserId) def value: UUID = id

    given CanEqual[UserId, UserId] = CanEqual.derived
    given JsonEncoder[UserId] = JsonEncoder.uuid
    given JsonDecoder[UserId] = JsonDecoder.uuid

    given MappedEncoding[UserId, UUID] = MappedEncoding(_.value)
  }

  opaque type WalletId = UUID
  object WalletId {
    def apply(uuid: UUID): WalletId = uuid
    def unapply(id: WalletId): UUID = id
    def random: WalletId = WalletId(UUID.randomUUID())

    extension (id: WalletId) def value: UUID = id

    given CanEqual[WalletId, WalletId] = CanEqual.derived
    given JsonEncoder[WalletId] = JsonEncoder.uuid
    given JsonDecoder[WalletId] = JsonDecoder.uuid

    given MappedEncoding[WalletId, UUID] = MappedEncoding(_.value)
  }

  enum Asset(val code: String, val decimals: Int) derives JsonCodec, CanEqual {
    case USD extends Asset("USD", 2)
    case BTC extends Asset("BTC", 8)
  }
  object Asset {
    def fromCode(code: String): Either[String, Asset] =
      code.trim.toUpperCase match {
        case "USD" => Right(Asset.USD)
        case "BTC" => Right(Asset.BTC)
        case other => Left(s"Unsupported asset: $other")
      }
  }
  final case class Wallet(
      id: WalletId,
      userId: UserId,
      asset: Asset,
      balanceMinor: Long,
      version: Long,
      createdAt: Instant
  ) derives JsonCodec

  opaque type LedgerTxId = UUID
  object LedgerTxId {
    def apply(uuid: UUID): LedgerTxId = uuid
    def unapply(id: LedgerTxId): UUID = id
    def random: LedgerTxId = LedgerTxId(UUID.randomUUID())

    extension (id: LedgerTxId) def value: UUID = id

    given CanEqual[LedgerTxId, LedgerTxId] = CanEqual.derived
    given JsonEncoder[LedgerTxId] = JsonEncoder.uuid
    given JsonDecoder[LedgerTxId] = JsonDecoder.uuid

    given MappedEncoding[LedgerTxId, UUID] = MappedEncoding(_.value)
  }
  final case class Principal(userId: UserId)

  enum DomainError(message: String) extends Throwable(message)
      derives CanEqual {
    case NotFound(message: String) extends DomainError(message)
    case Forbidden(message: String) extends DomainError(message)
    case Conflict(message: String) extends DomainError(message)
    case Validation(message: String) extends DomainError(message)
    case External(message: String) extends DomainError(message)
    case Internal(message: String) extends DomainError(message)
  }
}
