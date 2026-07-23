package services

import domain.*
import zio.*

// Representing regional payment providers
enum MobileOperator derives CanEqual {
  case MTN, Orange, Camtel
}

trait MobileMoneyService {
  def initiateCollection(
      phoneNumber: String,
      amount: BigDecimal,
      operator: MobileOperator
  ): Task[String] // Returns External Transaction Reference
}

case class MobileMoneyLive() extends MobileMoneyService {
  override def initiateCollection(
      phoneNumber: String,
      amount: BigDecimal,
      operator: MobileOperator
  ): Task[String] = {
    for {
      _ <- ZIO.logInfo(s"Sending STK Push to $phoneNumber via $operator")
      // In Cameroon, this would call an aggregator like Maviance (Smobilpay) or Moni
      refId <- ZIO.succeed(s"CM-TXN-${java.util.UUID.randomUUID()}")
    } yield refId
  }
}

object MobileMoneyService {
  val layer: ULayer[MobileMoneyService] = ZLayer.succeed(MobileMoneyLive())
}
