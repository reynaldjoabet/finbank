package com.migrantbank.service

import com.migrantbank.domain.{*, given}
import zio.*
import java.time.Instant
import java.util.UUID

/**
 * A locked FX rate with a short TTL.  The sender sees this rate before
 * confirming the transfer; the system honours it if confirmed within `ttlSeconds`.
 */
final case class LockedRate(
    id: UUID,
    fromCurrency: String,
    toCurrency: String,
    /** Minor units of `toCurrency` per 1 minor unit of `fromCurrency`. */
    rate: Double,
    lockedAt: Instant,
    expiresAt: Instant,
    idempotencyKey: String
) derives zio.json.JsonCodec

/**
 * Real-time FX rate locking service.
 *
 * Workflow:
 *   1. Caller invokes `lockRate(from, to, idempotencyKey)`.
 *   2. Service fetches a live mid-market rate (stub → replace with Yellow Card /
 *      Flutterwave FX API) and persists a `LockedRate` for `ttlSeconds`.
 *   3. Before a transfer is executed, `validate(id)` is called to ensure the
 *      rate has not expired.
 */
trait FxService {
  def lockRate(
      fromCurrency: String,
      toCurrency: String,
      idempotencyKey: String
  ): IO[AppError, LockedRate]

  def validate(lockedRateId: UUID): IO[AppError, LockedRate]

  /** Apply a locked rate to an amount in minor units of `fromCurrency`. */
  def convert(lockedRateId: UUID, amountMinor: Long): IO[AppError, Long]
}

object FxService {

  /** Locked rates expire after this many seconds (default: 30 s). */
  private val TtlSeconds = 30L

  /**
   * Stub rate table (mid-market reference rates, not real).
   * Production: replace with a live FX API call (Yellow Card, Flutterwave).
   */
  private val StubRates: Map[(String, String), Double] = Map(
    ("MUR", "XAF")  -> 14.5,
    ("MUR", "USDT") -> 0.022,
    ("USD", "XAF")  -> 625.0,
    ("USD", "XOF")  -> 625.0,
    ("USDT", "XAF") -> 625.0,
    ("USDT", "XOF") -> 625.0,
    ("NGN", "XAF")  -> 0.65,
    ("KES", "TZS")  -> 22.0
  )

  val live: ZLayer[Any, Nothing, FxService] =
    ZLayer.fromZIO(
      Ref.make(Map.empty[UUID, LockedRate]).map { store =>
        new FxService {

          override def lockRate(
              fromCurrency: String,
              toCurrency: String,
              idempotencyKey: String
          ): IO[AppError, LockedRate] = {
            // idempotency: if the same key is already locked and not expired, return it
            for {
              now <- Clock.instant
              existing <- store.get.map(
                _.values.find(r =>
                  r.idempotencyKey == idempotencyKey &&
                    r.fromCurrency == fromCurrency &&
                    r.toCurrency == toCurrency &&
                    r.expiresAt.isAfter(now)
                )
              )
              result <- existing match {
                case Some(r) => ZIO.succeed(r)
                case None    =>
                  val key = (fromCurrency.toUpperCase, toCurrency.toUpperCase)
                  StubRates.get(key).orElse(
                    // try inverse for symmetric pairs
                    StubRates.get((toCurrency.toUpperCase, fromCurrency.toUpperCase))
                      .map(r => if r != 0 then 1.0 / r else 0.0)
                  ) match {
                    case None =>
                      ZIO.fail(
                        AppError.Validation(
                          s"No FX rate available for $fromCurrency → $toCurrency"
                        )
                      )
                    case Some(rate) =>
                      for {
                        id <- Random.nextUUID
                        locked = LockedRate(
                          id = id,
                          fromCurrency = fromCurrency.toUpperCase,
                          toCurrency = toCurrency.toUpperCase,
                          rate = rate,
                          lockedAt = now,
                          expiresAt = now.plusSeconds(TtlSeconds),
                          idempotencyKey = idempotencyKey
                        )
                        _ <- store.update(_ + (id -> locked))
                        _ <- ZIO.logInfo(
                          s"[FxService] Locked rate $fromCurrency→$toCurrency @ $rate " +
                            s"expires=${locked.expiresAt}"
                        )
                      } yield locked
                  }
              }
            } yield result
          }

          override def validate(lockedRateId: UUID): IO[AppError, LockedRate] =
            for {
              now   <- Clock.instant
              rates <- store.get
              locked <- ZIO
                .fromOption(rates.get(lockedRateId))
                .orElseFail(AppError.NotFound(s"LockedRate $lockedRateId not found"))
              _ <- ZIO
                .fail(AppError.Validation("FX rate has expired; please lock a new rate"))
                .when(locked.expiresAt.isBefore(now))
            } yield locked

          override def convert(
              lockedRateId: UUID,
              amountMinor: Long
          ): IO[AppError, Long] =
            for {
              locked <- validate(lockedRateId)
              converted = (amountMinor * locked.rate).toLong
            } yield converted
        }
      }
    )
}
