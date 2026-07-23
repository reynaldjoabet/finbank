package services
import domain.*
import zio.*
trait AccountingSync {
  def sync(invoice: Invoice): Task[Unit]
}

case class AccountingSyncLive() extends AccountingSync {
  // Define an exponential backoff policy for flaky third-party APIs
  private val retryPolicy = Schedule.exponential(1.second) && Schedule.recurs(5)

  override def sync(invoice: Invoice): Task[Unit] = {
    ZIO
      .attempt {
        println(s"Exporting invoice ${invoice.id} to QuickBooks/Xero...")
        // Integration logic here
      }
      .retry(retryPolicy)
      .unit
  }
}

object AccountingSync {
  val layer: ULayer[AccountingSync] = ZLayer.succeed(AccountingSyncLive())
}
