package njangi

case class TrustReport(
    totalCyclesCompleted: Int,
    onTimePaymentRate: Double,
    peerVouchedScore: Int
)

object TrustCalculator {
  def updateScore(current: Int, onTime: Boolean): Int = {
    if (onTime) Math.min(1000, current + 10)
    else Math.max(0, current - 50) // Defaulting hits the score hard
  }
}
