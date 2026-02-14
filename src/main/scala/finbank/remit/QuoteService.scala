package finbank.remit

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.util.Random
import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs
import scala.math.BigDecimal.RoundingMode

object QuoteService {
// Models
  case class QuoteRequest(amountMUR: BigDecimal)
  case class RouteOption(
      name: String,
      steps: List[String],
      feePercentages: List[BigDecimal],
      deliveredAmountXAF: BigDecimal,
      totalLossPercent: BigDecimal
  )
  case class SendRequest(amountMUR: BigDecimal, chosenRoute: String)
  case class SendResult(
      success: Boolean,
      deliveredXAF: BigDecimal,
      message: String
  )

// Demo FX rates (replace with live rates in production)
  private val murToUsdRate = BigDecimal("0.023")
  private val usdToXafRate = BigDecimal("1000")

// Public API: compute best quotes
  def bestQuotes(req: QuoteRequest): List[RouteOption] = {
    val amt = req.amountMUR

// 1) Chained: MUR -> USD -> USDT -> XAF
    val chainedFees =
      List(BigDecimal("0.03"), BigDecimal("0.015"), BigDecimal("0.04"))
    val chainedDelivered = applyFeesSequential(
      amt,
      chainedFees,
      RouteHelpers.murToUsdConversionAndToXaf
    )
    val chainedLoss = percentLoss(amt, chainedDelivered)

// 2) Crypto-assisted: MUR -> USDT -> XAF
    val cryptoFees = List(BigDecimal("0.02"), BigDecimal("0.03"))
    val cryptoDelivered = applyFeesSequential(
      amt,
      cryptoFees,
      RouteHelpers.murToUsdConversionAndToXaf
    )
    val cryptoLoss = percentLoss(amt, cryptoDelivered)

// 3) Fiat-direct: MUR -> XAF via local partner
    val fiatFees = List(BigDecimal("0.025"))
    val fiatDelivered =
      applyFeesSequential(amt, fiatFees, RouteHelpers.murToXafConversion)
    val fiatLoss = percentLoss(amt, fiatDelivered)

    List(
      RouteOption(
        "Chained: MUR→USD→USDT→XAF",
        List("MUR→USD", "USD→USDT", "USDT→XAF"),
        chainedFees,
        chainedDelivered,
        chainedLoss
      ),
      RouteOption(
        "Crypto-assisted: MUR→USDT→XAF",
        List("MUR→USDT", "USDT→XAF"),
        cryptoFees,
        cryptoDelivered,
        cryptoLoss
      ),
      RouteOption(
        "Fiat-direct: MUR→XAF",
        List("MUR→XAF"),
        fiatFees,
        fiatDelivered,
        fiatLoss
      )
    )
  }

  def executeSend(req: SendRequest): SendResult = {
    val quotes = bestQuotes(QuoteRequest(req.amountMUR))
    quotes.find(
      _.name.toLowerCase.contains(req.chosenRoute.toLowerCase)
    ) match {
      case Some(route) =>
        SendResult(
          success = true,
          deliveredXAF = route.deliveredAmountXAF,
          message = s"Sent using ${route.name}"
        )
      case None =>
        SendResult(
          success = false,
          deliveredXAF = BigDecimal(0),
          message = "Route not found"
        )
    }
  }
  private def applyFeesSequential(
      amountMUR: BigDecimal,
      fees: List[BigDecimal],
      conversionFn: BigDecimal => BigDecimal
  ): BigDecimal = {
    val afterFees = fees.foldLeft(amountMUR) { (amt, fee) =>
      (amt * (BigDecimal(1) - fee)).setScale(8, RoundingMode.HALF_UP)
    }
    conversionFn(afterFees).setScale(2, RoundingMode.HALF_UP)
  }
  private def percentLoss(
      originalMUR: BigDecimal,
      deliveredXAF: BigDecimal
  ): BigDecimal = {
    val naiveXaf = RouteHelpers.murToXaf(originalMUR)
    if (naiveXaf == 0) then BigDecimal(0)
    else
      (((naiveXaf - deliveredXAF) / naiveXaf) * 100)
        .setScale(4, RoundingMode.HALF_UP)
  }
}
object RouteHelpers {
// Example conversion helper functions: tie MUR -> USD -> XAF chain for demo
  private val murToUsdRate = BigDecimal("0.023")
  private val usdToXafRate = BigDecimal("1000")

  def murToUsdConversion(mur: BigDecimal): BigDecimal =
    (mur * murToUsdRate).setScale(6, RoundingMode.HALF_UP)

  def murToXaf(mur: BigDecimal): BigDecimal =
    (mur * murToUsdRate * usdToXafRate).setScale(2, RoundingMode.HALF_UP)

  def murToUsdConversionAndToXaf(mur: BigDecimal): BigDecimal =
// convert MUR -> USD, then USD -> XAF (used by chained/crypto routes demo)
    (mur * murToUsdRate * usdToXafRate).setScale(2, RoundingMode.HALF_UP)

  def murToXafConversion(mur: BigDecimal): BigDecimal =
// direct MUR->XAF conversion (demo uses same parity but could differ when using live rates)
    murToXaf(mur)
}
