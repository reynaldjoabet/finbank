package unitypay
import unitypay.providers.{MtnMomoAdapter, OrangeMomoAdapter, WaveAdapter}
import zio.*
import zio.json.*
import java.time.Instant

/** ISO 4217 currency codes used across African mobile money networks. */
enum Currency(val code: String) derives CanEqual {
  case XAF extends Currency("XAF") // CFA Franc BEAC (CEMAC)
  case XOF extends Currency("XOF") // CFA Franc BCEAO (WAEMU)
  case NGN extends Currency("NGN") // Nigerian Naira
  case KES extends Currency("KES") // Kenyan Shilling
  case GHS extends Currency("GHS") // Ghanaian Cedi
  case TZS extends Currency("TZS") // Tanzanian Shilling
  case UGX extends Currency("UGX") // Ugandan Shilling
  case MUR extends Currency("MUR") // Mauritian Rupee
  case USDC extends Currency("USDC") // USD Coin (stablecoin)
  case USDT extends Currency("USDT") // Tether (stablecoin)
}

object Currency {
  given JsonEncoder[Currency] = JsonEncoder.string.contramap(_.code)
  given JsonDecoder[Currency] = JsonDecoder.string.mapOrFail { s =>
    values.find(_.code == s).toRight(s"Unknown currency: $s")
  }
}

/** Amount in minor units (e.g. centimes for XAF, kobo for NGN). */
final case class Amount(minor: Long, currency: Currency) derives JsonCodec

enum MoMoProvider derives CanEqual, JsonCodec {
  case MtnMomo // MTN Mobile Money (active in 17 African countries)
  case OrangeMoney // Orange Money (CEMAC / WAEMU zone)
  case Wave // Wave (Senegal, Ivory Coast — near-zero fees)
  case MPesa // M-Pesa (Kenya, Tanzania, Mozambique, …)
  case AirtelMoney // Airtel Money (East/Central Africa)
}

enum MoMoTransferStatus derives CanEqual, JsonCodec {
  case Pending, Successful, Failed, Cancelled
}

final case class MoMoTransferRequest(
    provider: MoMoProvider,
    senderPhone: E164Phone,
    recipientPhone: E164Phone,
    amount: Amount,
    idempotencyKey: String,
    note: Option[String]
) derives JsonCodec

final case class MoMoTransferResult(
    providerRef: String,
    status: MoMoTransferStatus,
    fee: Option[Amount],
    initiatedAt: Instant
) derives JsonCodec

object MoMoTransferResult {
  given JsonEncoder[Instant] = JsonEncoder.string.contramap(_.toString)
  given JsonDecoder[Instant] = JsonDecoder.string.map(Instant.parse)
}

opaque type E164Phone = String
object E164Phone {
  def apply(s: String): E164Phone = s
  def unapply(p: E164Phone): String = p
  def parse(s: String): Either[String, E164Phone] =
    if s.matches("""\+\d{7,15}""") then Right(s)
    else Left(s"Invalid E.164 phone number: $s")

  extension (p: E164Phone) def value: String = p

  given CanEqual[E164Phone, E164Phone] = CanEqual.derived
  given JsonEncoder[E164Phone] = JsonEncoder.string
  given JsonDecoder[E164Phone] = JsonDecoder.string
}

/** Provider error returned by the underlying MoMo API. */
sealed trait MoMoError extends Throwable {
  def message: String
  override def getMessage(): String = message
}
object MoMoError {
  final case class InsufficientFunds(message: String) extends MoMoError
  final case class InvalidRecipient(message: String) extends MoMoError
  final case class ProviderTimeout(message: String) extends MoMoError
  final case class ProviderError(message: String) extends MoMoError
  final case class UnsupportedCurrency(message: String) extends MoMoError
}

/** Single abstraction for all pan-African mobile money providers.
  *
  * Each provider (`MtnMomoAdapter`, `OrangeMomoAdapter`, …) implements this
  * trait. The `InteropRouter` dispatches based on `provider` field.
  */
trait MobileMoneyProvider {
  def provider: MoMoProvider

  /** Initiate a mobile money push to a recipient. */
  def send(
      req: MoMoTransferRequest
  ): IO[MoMoError, MoMoTransferResult]

  /** Poll an in-flight transaction by the provider's own reference. */
  def status(providerRef: String): IO[MoMoError, MoMoTransferStatus]

  /** Health check — returns `true` if the upstream API is reachable. */
  def healthCheck: UIO[Boolean]
}

/** Routes a `MoMoTransferRequest` to the correct `MobileMoneyProvider` based on
  * the `provider` field. This is the single entry-point for all pan-African
  * mobile money payments inside `unity-pay`.
  *
  * Usage:
  * {{{
  *   val router: InteropRouter = ???
  *   router.route(req).mapError(handleMoMoError)
  * }}}
  */
final class InteropRouter(providers: Map[MoMoProvider, MobileMoneyProvider]) {

  def route(req: MoMoTransferRequest): IO[MoMoError, MoMoTransferResult] =
    providers.get(req.provider) match {
      case Some(p) => p.send(req)
      case None    =>
        ZIO.fail(
          MoMoError.ProviderError(s"No adapter registered for ${req.provider}")
        )
    }

  def pollStatus(
      provider: MoMoProvider,
      providerRef: String
  ): IO[MoMoError, MoMoTransferStatus] =
    providers.get(provider) match {
      case Some(p) => p.status(providerRef)
      case None    =>
        ZIO.fail(
          MoMoError.ProviderError(s"No adapter registered for $provider")
        )
    }

  /** Returns a map of provider → reachable. */
  def healthAll: UIO[Map[MoMoProvider, Boolean]] =
    ZIO
      .foreachPar(providers.toList) { case (k, v) =>
        v.healthCheck.map(k -> _)
      }
      .map(_.toMap)
}

object InteropRouter {

  val layer: ZLayer[
    MtnMomoAdapter & OrangeMomoAdapter & WaveAdapter,
    Nothing,
    InteropRouter
  ] =
    ZLayer.fromFunction {
      (mtn: MtnMomoAdapter, orange: OrangeMomoAdapter, wave: WaveAdapter) =>
        new InteropRouter(
          Map(
            MoMoProvider.MtnMomo -> (mtn: MobileMoneyProvider),
            MoMoProvider.OrangeMoney -> (orange: MobileMoneyProvider),
            MoMoProvider.Wave -> (wave: MobileMoneyProvider)
          )
        )
    }
}
