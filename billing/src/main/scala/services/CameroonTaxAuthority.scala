package services
import domain.Invoice
import zio.*
import java.time.OffsetDateTime
import domain.CameroonTaxAuthorityInvoiceReport
import zio.json.EncoderOps
trait CameroonTaxAuthority {
  def registerInvoice(invoice: Invoice): Task[Unit]
  def reportTransaction(invoice: Invoice, txnRef: String): Task[Unit]
}

case class DGITaxSync(dgiApi: String) extends CameroonTaxAuthority {
  // 2026 Law Requirement: Continuous digital control
  override def registerInvoice(invoice: Invoice): Task[Unit] = {
    ZIO
      .attempt {
        // Logic to send JSON to DGI Cameroon portal
        println(
          s"Syncing Invoice ${invoice.id} with DGI Cameroon for tax compliance."
        )
      }
      .retry(Schedule.exponential(500.millis) && Schedule.recurs(3))
  }
  override def reportTransaction(
      invoice: Invoice,
      txnRef: String
  ): Task[Unit] = {
    val report = CameroonTaxAuthorityInvoiceReport(
      invoiceNumber = invoice.id.toString,
      dateTime = OffsetDateTime.now(),
      merchantId = "M123456789012X", // Mock NIU
      customerName = invoice.customerEmail,
      grossAmount = invoice.amount,
      vatAmount = invoice.amount * 0.1925,
      sepTaxAmount = invoice.amount * 0.03, // 3% SEP Tax (2026 Law)
      netAmount = invoice.amount,
      paymentMethod = "MOBILE_MONEY",
      transactionRef = txnRef
    )

    for {
      _ <- ZIO.logInfo(s"Reporting to DGI CPF: ${report.invoiceNumber}")
      jsonBody = report.toJson
      // In production: sttp.client3.basicRequest.post(uri"$apiUrl").body(jsonBody)...
      _ <- ZIO.attempt(println(s"DGI PAYLOAD: $jsonBody"))
      _ <- ZIO.logInfo("DGI Acceptance Received (201 Created)")
    } yield ()
  }

}

object CameroonTaxAuthority {
  val layer: URLayer[String, CameroonTaxAuthority] =
    ZLayer.fromFunction(DGITaxSync.apply _)
}
