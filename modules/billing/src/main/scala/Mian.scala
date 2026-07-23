package domain

import zio.*
import services.*
import domain.*

object Main extends ZIOAppDefault {

  val program = for {
    engine <- ZIO.service[BillingService]
    result <- engine.processNewOrder(2500.00, "finance@global-tech.com")
    _ <- Console.printLine(s"Successfully processed invoice: ${result.id}")
  } yield ()

  override def run = program.provide(
    BillingService.layer,
    DwollaService.layer,
    PlaidService.layer,
    AccountingSync.layer
  )
}
