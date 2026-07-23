package services

import domain.*
import zio.*

trait DwollaService {
  def performKYB(email: String): Task[Unit]
  def initiateACH(amount: BigDecimal, invoiceId: InvoiceId): Task[String]
  def chargeACH(invoice: Invoice): Task[Unit]
}

case class DwollaServiceLive() extends DwollaService {
  override def performKYB(email: String): Task[Unit] = {
    ZIO.logInfo(s"Running KYB for $email via Dwolla...") *>
      ZIO.sleep(500.millis) // Simulating network latency
  }

  override def initiateACH(
      amount: BigDecimal,
      invoiceId: InvoiceId
  ): Task[String] = {
    for {
      _ <- ZIO.logInfo(
        s"Initiating ACH Transfer for $amount (Invoice: $invoiceId)"
      )
      txnId = s"dw-txn-${java.util.UUID.randomUUID()}"
    } yield txnId
  }
  override def chargeACH(invoice: Invoice): Task[Unit] = {
    for {
      txnId <- initiateACH(invoice.amount, invoice.id)
      _ <- ZIO.logInfo(
        s"Charged ${invoice.amount} for Invoice ${invoice.id} (Transaction ID: $txnId)"
      )
    } yield ()
  }
}

object DwollaService {
  val layer: ULayer[DwollaService] = ZLayer.succeed(DwollaServiceLive())
}
