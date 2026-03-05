import services.CameroonRevenueAuthority
import zio.*
import domain.*
import services.PayoutService
import zio.stream.ZStream
object PayoutWorker {
  def start: ZIO[PayoutService, Throwable, Unit] = {
    val pendingPayments = Chunk(
      PayoutRequest("+237671234567", 50000, "Salary Jan"),
      PayoutRequest("+237699887766", 25000, "Tontine Payout")
    )

    for {
      service <- ZIO.service[PayoutService]
      _ <- ZStream
        .tick(5.minutes) // Run every 5 minutes
        .mapZIO(_ => service.processBulk(pendingPayments).runCollect)
        .runDrain
    } yield ()
  }
}
