package unitypay.providers

import unitypay.*
import zio.*

/**
 * Wave adapter (Senegal, Côte d'Ivoire — near-zero fee model).
 *
 * Wave charges ~0% for domestic transfers, making it the preferred rail for
 * WAEMU-zone intra-country payments.  Stub — replace with Wave Business API
 * calls once credentials are available.
 */
final class WaveAdapter extends MobileMoneyProvider {

  override val provider: MoMoProvider = MoMoProvider.Wave

  private val supportedCurrencies = Set(Currency.XOF, Currency.XAF)

  override def send(
      req: MoMoTransferRequest
  ): IO[MoMoError, MoMoTransferResult] =
    for {
      _ <- ZIO
        .fail(
          MoMoError.UnsupportedCurrency(
            s"Wave does not support ${req.amount.currency.code}"
          )
        )
        .when(!supportedCurrencies.contains(req.amount.currency))
      _ <- ZIO
        .fail(MoMoError.InsufficientFunds("Amount must be > 0"))
        .when(req.amount.minor <= 0)
      _ <- ZIO.logInfo(
        s"[Wave] Initiating transfer: ${req.amount.minor} ${req.amount.currency.code} " +
          s"→ ${req.recipientPhone.value}"
      )
      now <- Clock.instant
      ref <- Random.nextUUID.map(u => s"WAVE-$u")
      // Wave charges 0 fee for standard transfers
      result = MoMoTransferResult(
        providerRef = ref,
        status = MoMoTransferStatus.Pending,
        fee = Some(Amount(0L, req.amount.currency)),
        initiatedAt = now
      )
    } yield result

  override def status(providerRef: String): IO[MoMoError, MoMoTransferStatus] =
    ZIO.logInfo(s"[Wave] Polling status for $providerRef") *>
      ZIO.succeed(MoMoTransferStatus.Successful)

  override val healthCheck: UIO[Boolean] = ZIO.succeed(true)
}

object WaveAdapter {
  val layer: ULayer[WaveAdapter] = ZLayer.succeed(new WaveAdapter)
}
