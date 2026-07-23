package njangi
import zio.*
abstract class MoMoClient {
  def initiateTransfer(payload: String): Task[Unit]
  def fetchAccountData(accountId: String): Task[String]
  def refundTransfer(transferId: String, reason: String): Task[Unit]

  def requestPayment(phone: String, amount: BigDecimal): Task[PaymentStatus]
}
