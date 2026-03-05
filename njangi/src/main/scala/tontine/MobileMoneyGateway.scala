package tontine
import zio.*
trait MobileMoneyGateway {
  def requestPush(
      phoneE164: String,
      amount: Money,
      reference: String
  ): IO[AppError.Payment, MobileMoneyTxId]
  def getStatus(txId: MobileMoneyTxId): IO[AppError.Payment, PaymentStatus]

}

final case class MobileMoneyGatewayLive() extends MobileMoneyGateway {
  override def requestPush(
      phoneE164: String,
      amount: Money,
      reference: String
  ): IO[AppError.Payment, MobileMoneyTxId] = ???
  override def getStatus(
      txId: MobileMoneyTxId
  ): IO[AppError.Payment, PaymentStatus] = ???
}

object MobileMoneyGateway {
  val layer: ULayer[MobileMoneyGateway] =
    ZLayer.succeed(MobileMoneyGatewayLive())
}
