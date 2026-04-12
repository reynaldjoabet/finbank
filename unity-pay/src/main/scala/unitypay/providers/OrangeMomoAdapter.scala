package unitypay.providers

import unitypay.*
import zio.*

/**
 * Orange Money adapter (Cameroon, Côte d'Ivoire, Senegal, Mali, …).
 *
 * Calls Orange Money Merchant API.  Stub implementation — replace with real
 * HTTP calls once API keys are provisioned.
 */
final class OrangeMomoAdapter extends MobileMoneyProvider {

  override val provider: MoMoProvider = MoMoProvider.OrangeMoney

  // Orange Money primarily serves the XAF and XOF zones.
  private val supportedCurrencies =
    Set(Currency.XAF, Currency.XOF, Currency.GHS)

  override def send(
      req: MoMoTransferRequest
  ): IO[MoMoError, MoMoTransferResult] =
    for {
      _ <- ZIO
        .fail(
          MoMoError.UnsupportedCurrency(
            s"Orange Money does not support ${req.amount.currency.code}"
          )
        )
        .when(!supportedCurrencies.contains(req.amount.currency))
      _ <- ZIO
        .fail(MoMoError.InsufficientFunds("Amount must be > 0"))
        .when(req.amount.minor <= 0)
      _ <- ZIO.logInfo(
        s"[Orange Money] Initiating transfer: ${req.amount.minor} ${req.amount.currency.code} " +
          s"→ ${req.recipientPhone.value}"
      )
      now <- Clock.instant
      ref <- Random.nextUUID.map(u => s"OM-$u")
      result = MoMoTransferResult(
        providerRef = ref,
        status = MoMoTransferStatus.Pending,
        fee = Some(Amount((req.amount.minor * 0.01).toLong.max(1L), req.amount.currency)),
        initiatedAt = now
      )
    } yield result

  override def status(providerRef: String): IO[MoMoError, MoMoTransferStatus] =
    ZIO.logInfo(s"[Orange Money] Polling status for $providerRef") *>
      ZIO.succeed(MoMoTransferStatus.Successful)

  override val healthCheck: UIO[Boolean] = ZIO.succeed(true)
}

object OrangeMomoAdapter {
  val layer: ULayer[OrangeMomoAdapter] = ZLayer.succeed(new OrangeMomoAdapter)
}
