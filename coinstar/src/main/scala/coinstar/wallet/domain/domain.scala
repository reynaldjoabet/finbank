package coinstar.wallet
import zio.json.*
import java.time.Instant
import java.util.UUID
import io.getquill.*
package object domain {
  opaque type UserId = UUID
  object UserId {
    def apply(uuid: UUID): UserId = uuid
    def random: UserId = UserId(UUID.randomUUID())
    def fromString(s: String): Either[String, UserId] =
      try Right(UserId(UUID.fromString(s)))
      catch {
        case e: IllegalArgumentException =>
          Left(s"Invalid UUID in subject claim: ${e.getMessage}")
      }

    extension (id: UserId) def value: UUID = id

    given JsonEncoder[UserId] =
      JsonEncoder.uuid.contramap[UserId](_.value)
    given JsonDecoder[UserId] = JsonDecoder.uuid

    given MappedEncoding[UserId, UUID] = MappedEncoding(_.value)
  }

  opaque type WalletId = UUID
  object WalletId {
    def apply(uuid: UUID): WalletId = uuid
    def random: WalletId = WalletId(UUID.randomUUID())

    extension (id: WalletId) def value: UUID = id
    given JsonEncoder[WalletId] =
      JsonEncoder.uuid.contramap[WalletId](_.value)
    given JsonDecoder[WalletId] = JsonDecoder.uuid

    given MappedEncoding[WalletId, UUID] = MappedEncoding(_.value)
  }

  enum Asset(val code: String, val decimals: Int) derives JsonCodec {
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
    def random: LedgerTxId = LedgerTxId(UUID.randomUUID())
    extension (id: LedgerTxId) def value: UUID = id
    given JsonEncoder[LedgerTxId] =
      JsonEncoder.uuid.contramap[LedgerTxId](_.value)
    given JsonDecoder[LedgerTxId] = JsonDecoder.uuid.map(LedgerTxId(_))

    given MappedEncoding[LedgerTxId, UUID] = MappedEncoding(_.value)
  }
  final case class Principal(userId: UserId)

  enum DomainError(message: String) extends Throwable(message) {
    case NotFound(message: String) extends DomainError(message)
    case Forbidden(message: String) extends DomainError(message)
    case Conflict(message: String) extends DomainError(message)
    case Validation(message: String) extends DomainError(message)
    case External(message: String) extends DomainError(message)
    case Internal(message: String) extends DomainError(message)
  }
}
