package services

import domain.*
import zio.*

trait BillingService {
  def processNewOrder(amount: BigDecimal, email: String): Task[Invoice]
}

case class BillingServiceLive(
    dwolla: DwollaService,
    plaid: PlaidService,
    accounting: AccountingSync
) extends BillingService {

  override def processNewOrder(
      amount: BigDecimal,
      email: String
  ): Task[Invoice] = {
    for {
      id <- InvoiceId.random
      _ <- ZIO.logInfo(s"Initiating autonomous billing for: $id")

      // Verify bank via Plaid & Perform KYB via Dwolla in parallel
      _ <- plaid.verifyAccount(email) zipPar dwolla.performKYB(email)

      invoice = Invoice(id, amount, email, PaymentStatus.Processing)

      // Execute payment and sync to Xero/QuickBooks
      _ <- dwolla.chargeACH(invoice) zipPar accounting.sync(invoice)

    } yield invoice
  }
}

object BillingService {
  val layer
      : URLayer[DwollaService & PlaidService & AccountingSync, BillingService] =
    ZLayer.fromFunction(BillingServiceLive.apply _)
}
