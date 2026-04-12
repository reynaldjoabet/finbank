package unitypay.providers

import unitypay.*
import zio.*
import java.time.Instant
import java.util.UUID

/**
 * MTN Mobile Money adapter.
 *
 * In production this calls the MTN MoMo OpenAPI (sandbox at
 * https://sandbox.momodeveloper.mtn.com).  All real HTTP calls are stubbed
 * here; replace the `ZIO.succeed(...)` stubs with sttp / zio-http calls once
 * the provider credentials are available in `AppConfig`.
 *
 * Supported currencies: XAF (CEMAC), XOF (WAEMU), GHS, UGX, ZMW, …
 */
final class MtnMomoAdapter extends MobileMoneyProvider {

  override val provider: MoMoProvider = MoMoProvider.MtnMomo

  override def send(
      req: MoMoTransferRequest
  ): IO[MoMoError, MoMoTransferResult] = {
    val supportedCurrencies =
      Set(Currency.XAF, Currency.XOF, Currency.GHS, Currency.UGX)
    for {
      _ <- ZIO
        .fail(
          MoMoError.UnsupportedCurrency(
            s"MTN MoMo does not support ${req.amount.currency.code}"
          )
        )
        .when(!supportedCurrencies.contains(req.amount.currency))
      _ <- ZIO
        .fail(MoMoError.InsufficientFunds("Amount must be > 0"))
        .when(req.amount.minor <= 0)
      // --- stub: replace with real sttp call to MTN MoMo Collections API ---
      _ <- ZIO.logInfo(
        s"[MTN MoMo] Initiating transfer: ${req.amount.minor} ${req.amount.currency.code} " +
          s"→ ${req.recipientPhone.value} idem=${req.idempotencyKey}"
      )
      now <- Clock.instant
      ref <- Random.nextUUID.map(_.toString)
      result = MoMoTransferResult(
        providerRef = s"MTN-$ref",
        status = MoMoTransferStatus.Pending,
        fee = Some(Amount((req.amount.minor * 0.01).toLong.max(1L), req.amount.currency)),
        initiatedAt = now
      )
    } yield result
  }

  override def status(providerRef: String): IO[MoMoError, MoMoTransferStatus] =
    ZIO.logInfo(s"[MTN MoMo] Polling status for $providerRef") *>
      ZIO.succeed(MoMoTransferStatus.Successful)

  override val healthCheck: UIO[Boolean] =
    ZIO.succeed(true)
}

object MtnMomoAdapter {
  val layer: ULayer[MtnMomoAdapter] = ZLayer.succeed(new MtnMomoAdapter)
}
