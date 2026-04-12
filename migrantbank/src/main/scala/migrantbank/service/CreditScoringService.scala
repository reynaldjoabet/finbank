package com.migrantbank.service

import com.migrantbank.domain.*
import zio.*
import java.util.UUID

/**
 * Credit band derived from alternative data (tontine history + MoMo activity).
 *
 *   Thin   → no formal history; micro-loan only
 *   Fair   → some history; standard micro-loan
 *   Good   → solid history; upgraded limits
 *   Strong → excellent track record; best rates
 */
enum CreditBand derives CanEqual, zio.json.JsonCodec {
  case Thin, Fair, Good, Strong
}

/**
 * Parameters associated with a credit band.
 *
 * @param maxLoanMinor    Maximum loan principal in minor units (USD cents).
 * @param annualRateBps   Annual interest rate in basis points (100 bps = 1 %).
 * @param termDays        Default loan term in days.
 * @param collateralReq   Whether physical collateral is required.
 */
final case class BandParams(
    band: CreditBand,
    maxLoanMinor: Long,
    annualRateBps: Int,
    termDays: Int,
    collateralReq: Boolean
) derives zio.json.JsonCodec

/**
 * Credit score result combining tontine contribution data with MoMo activity.
 */
final case class CreditScore(
    userId: UUID,
    tontineScore: Option[Int],           // 0–100 from njangi export (may be absent)
    momoTransactionCount: Int,
    momoTotalVolumeMinor: Long,
    band: CreditBand,
    params: BandParams,
    computedAt: java.time.Instant
) derives zio.json.JsonCodec

/**
 * Alternative credit scoring service.
 *
 * Uses:
 *   1. **Tontine contribution history** — on-time rate from the njangi circle
 *      (imported as a portable JSON credential via `ScoreService.exportSignedCredential`).
 *   2. **MoMo transaction history** — volume and frequency of mobile money
 *      transactions (a proxy for economic activity).
 *
 * This is the service that replaces formal credit bureaus for the ~60 %
 * of African adults who have no bank-reportable credit history.
 */
trait CreditScoringService {
  def score(userId: UUID): IO[AppError, CreditScore]
  def bandParams(band: CreditBand): BandParams
}

object CreditScoringService {

  /** Band → parameters lookup table. */
  val Params: Map[CreditBand, BandParams] = Map(
    CreditBand.Thin   -> BandParams(CreditBand.Thin,   2_000_00L,  3600, 14,  true),
    CreditBand.Fair   -> BandParams(CreditBand.Fair,   5_000_00L,  2400, 30,  false),
    CreditBand.Good   -> BandParams(CreditBand.Good,  15_000_00L,  1800, 60,  false),
    CreditBand.Strong -> BandParams(CreditBand.Strong, 50_000_00L, 1200, 90,  false)
  )

  private def classifyBand(
      tontineScore: Option[Int],
      txCount: Int,
      volumeMinor: Long
  ): CreditBand = {
    val tontinePoints =
      tontineScore.map(s => if s >= 80 then 40 else if s >= 50 then 25 else 10).getOrElse(0)
    val momoPoints =
      (if txCount >= 20 then 30 else if txCount >= 10 then 20 else if txCount >= 3 then 10 else 0) +
        (if volumeMinor >= 1_000_00L then 30 else if volumeMinor >= 500_00L then 20 else if volumeMinor >= 100_00L then 10 else 0)
    val total = tontinePoints + momoPoints
    if total >= 85      then CreditBand.Strong
    else if total >= 55 then CreditBand.Good
    else if total >= 30 then CreditBand.Fair
    else                     CreditBand.Thin
  }

  val live: ZLayer[LoanService, Nothing, CreditScoringService] =
    ZLayer.fromFunction { (loans: LoanService) =>
      new CreditScoringService {

        override def score(userId: UUID): IO[AppError, CreditScore] =
          for {
            // Use existing loan/transfer history as a proxy for MoMo activity.
            // Production: also call MoMo provider API for transaction history.
            txList <- loans.list(userId)
            txCount = txList.size
            volumeMinor = txList.map(_.principal.amountMinor).sum

            // Tontine score: in production, fetch the signed credential from
            // `njangi` service and parse the embedded score.  Here we stub to None.
            tontineScore: Option[Int] = None

            band = classifyBand(tontineScore, txCount, volumeMinor)
            now  <- Clock.instant
          } yield CreditScore(
            userId = userId,
            tontineScore = tontineScore,
            momoTransactionCount = txCount,
            momoTotalVolumeMinor = volumeMinor,
            band = band,
            params = Params(band),
            computedAt = now
          )

        override def bandParams(band: CreditBand): BandParams =
          Params(band)
      }
    }
}
