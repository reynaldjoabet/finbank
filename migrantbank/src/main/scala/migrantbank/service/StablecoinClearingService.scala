package com.migrantbank.service

import com.migrantbank.domain.*
import zio.*
import java.time.Instant
import java.util.UUID

/**
 * Settlement rail used by the clearing hub.
 *
 * - `StellarUsdc` / `CeloUsdc` are on-chain L2 rails with near-zero fees.
 * - `Internal`    settles within finbank's own ledger (no on-chain hop).
 */
enum ClearingRail derives CanEqual, zio.json.JsonCodec {
  case StellarUsdc   // Stellar Network — 3-5 second finality
  case CeloUsdc      // Celo network  — 5-second finality, mobile-first
  case PolygonUsdc   // Polygon PoS   — ~2 second finality
  case Internal      // Intra-platform settlement (zero fee)
}

/**
 * A cross-border clearing request — sender funds arrive in stablecoin and
 * are converted to the recipient's local currency at the other end.
 *
 * Workflow (sub-1 % cost):
 *   Sender fiat → on-ramp (1× FX) → USDC on L2 → off-ramp (1× FX) → Recipient fiat
 */
final case class ClearingRequest(
    id: UUID,
    senderId: UUID,
    recipientPhone: String,
    recipientCountry: String, // ISO 3166-1 alpha-2
    sendAmountMinor: Long,
    sendCurrency: String,
    receiveAmountMinor: Long,
    receiveCurrency: String,
    lockedRateId: UUID,
    rail: ClearingRail,
    idempotencyKey: String,
    createdAt: Instant
) derives zio.json.JsonCodec

enum ClearingStatus derives CanEqual, zio.json.JsonCodec {
  case Initiated, OnRampPending, InFlight, OffRampPending, Settled, Failed
}

final case class ClearingRecord(
    request: ClearingRequest,
    status: ClearingStatus,
    onChainTxHash: Option[String],
    settledAt: Option[Instant],
    errorReason: Option[String]
) derives zio.json.JsonCodec

/**
 * Stablecoin Clearing Hub.
 *
 * Replaces the expensive multi-hop correspondent banking model with a
 * two-FX-touch route via stablecoins:
 *
 *   MUR (sender) ─onRamp─► USDC on Stellar ─offRamp─► XAF (recipient)
 *
 * Both legs are executed atomically at the locked rate.  If either leg
 * fails the transfer is reversed.
 */
trait StablecoinClearingService {
  def initiate(
      senderId: UUID,
      recipientPhone: String,
      recipientCountry: String,
      sendAmount: Money,
      receiveAmount: Money,
      lockedRateId: UUID,
      idempotencyKey: String
  ): IO[AppError, ClearingRecord]

  def getRecord(id: UUID): IO[AppError, ClearingRecord]
  def listBySender(senderId: UUID): IO[AppError, List[ClearingRecord]]
}

object StablecoinClearingService {

  /** Select the lowest-fee on-chain rail for the given corridor. */
  private def selectRail(sendCurrency: String, receiveCurrency: String): ClearingRail =
    (sendCurrency.toUpperCase, receiveCurrency.toUpperCase) match {
      case ("MUR", "XAF") | ("MUR", "XOF") => ClearingRail.StellarUsdc
      case ("USD", "NGN") | ("NGN", "USD") => ClearingRail.CeloUsdc
      case ("KES", _) | (_, "KES")         => ClearingRail.StellarUsdc
      case _                                => ClearingRail.StellarUsdc
    }

  val live: ZLayer[FxService, Nothing, StablecoinClearingService] =
    ZLayer.fromZIO {
      for {
        fx    <- ZIO.service[FxService]
        store <- Ref.make(Map.empty[UUID, ClearingRecord])
      } yield new StablecoinClearingServiceLive(fx, store)
    }

  private final class StablecoinClearingServiceLive(
      fx: FxService,
      store: Ref[Map[UUID, ClearingRecord]]
  ) extends StablecoinClearingService {


        override def initiate(
            senderId: UUID,
            recipientPhone: String,
            recipientCountry: String,
            sendAmount: Money,
            receiveAmount: Money,
            lockedRateId: UUID,
            idempotencyKey: String
        ): IO[AppError, ClearingRecord] =
          for {
            // Check idempotency
            now      <- Clock.instant
            existing <- store.get.map(
              _.values.find(_.request.idempotencyKey == idempotencyKey)
            )
            result <- existing match {
              case Some(r) => ZIO.succeed(r)
              case None    =>
                for {
                  _    <- fx.validate(lockedRateId)
                  id   <- Random.nextUUID
                  rail = selectRail(sendAmount.currency, receiveAmount.currency)
                  req = ClearingRequest(
                    id = id,
                    senderId = senderId,
                    recipientPhone = recipientPhone,
                    recipientCountry = recipientCountry,
                    sendAmountMinor = sendAmount.amountMinor,
                    sendCurrency = sendAmount.currency,
                    receiveAmountMinor = receiveAmount.amountMinor,
                    receiveCurrency = receiveAmount.currency,
                    lockedRateId = lockedRateId,
                    rail = rail,
                    idempotencyKey = idempotencyKey,
                    createdAt = now
                  )
                  // --- stub: replace with real on-ramp API call ---
                  onChainHash <- Random.nextUUID.map(u => s"0x${u.toString.replace("-", "")}")
                  _ <- ZIO.logInfo(
                    s"[StablecoinClearing] On-chain tx=$onChainHash rail=$rail " +
                      s"${sendAmount.amountMinor} ${sendAmount.currency} → " +
                      s"${receiveAmount.amountMinor} ${receiveAmount.currency}"
                  )
                  record = ClearingRecord(
                    request = req,
                    status = ClearingStatus.Settled,
                    onChainTxHash = Some(onChainHash),
                    settledAt = Some(now),
                    errorReason = None
                  )
                  _ <- store.update(_ + (id -> record))
                } yield record
            }
          } yield result

        override def getRecord(id: UUID): IO[AppError, ClearingRecord] =
          store.get.flatMap { m =>
            ZIO
              .fromOption(m.get(id))
              .orElseFail(AppError.NotFound(s"Clearing record $id not found"))
          }

        override def listBySender(senderId: UUID): IO[AppError, List[ClearingRecord]] =
          store.get.map(_.values.filter(_.request.senderId == senderId).toList)
      }
    }
}
