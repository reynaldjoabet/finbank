package tontine.service
import zio.*
import tontine.config.AppConfig
import java.util.UUID
import tontine.{TontineCircle, PaymentCycle}
import tontine.MemberRepo

trait MoMoService {
  def collectFunds(phoneNumber: String, amount: BigDecimal): Task[Boolean]
  def disburseFunds(phoneNumber: String, amount: BigDecimal): Task[Boolean]
}

case class MoMoServiceLive(config: AppConfig) extends MoMoService {
  def collectFunds(phoneNumber: String, amount: BigDecimal): Task[Boolean] =
    ZIO.logInfo(s"Requesting $amount from $phoneNumber...") *>
      // Here you would call the MTN/Orange API via sttp or http4s
      ZIO.succeed(true)

  def disburseFunds(phoneNumber: String, amount: BigDecimal): Task[Boolean] =
    ZIO.logInfo(s"Sending $amount to $phoneNumber...") *>
      ZIO.succeed(true)
}

object MoMoServiceLive {
  val layer: URLayer[AppConfig, MoMoService] =
    ZLayer.fromFunction(MoMoServiceLive(_))
}

def processPaymentCycle(
    circle: TontineCircle,
    cycle: PaymentCycle
): ZIO[MoMoService, Throwable, Unit] = {
  for {
    momo <- ZIO.service[MoMoService]
    /// memberRepo<- ZIO.service[MemberRepo]
    _ <- ZIO.logInfo(
      s"Starting collection for Circle: ${circle.name}, Round: ${cycle.roundNumber}"
    )

    // 1. Collection Phase: Pull funds from all members concurrently
    results <- ZIO.foreachPar(circle.members) { member =>
      momo
        .collectFunds(member.phoneNumber, circle.contributionAmount)
        .retry(Schedule.recurs(3) && Schedule.exponential(1.second))
        .catchAll { e =>
          ZIO.logError(s"Failed to collect from ${member.fullName}: $e") *>
            ZIO.succeed(false)
        }
    }

    successfulCollections = results.count { res => res == true }
    totalCollected = circle.contributionAmount * successfulCollections

    // 2. Payout Phase: Send the collected pool to the winner of this cycle
    _ <- ZIO.when(totalCollected > 0) {
      circle.members.find { member => member.id == cycle.winnerId } match {
        case Some(winner) =>
          momo.disburseFunds(winner.phoneNumber, totalCollected) *>
            ZIO.logInfo(
              s"Successfully disbursed $totalCollected CFA to ${winner.fullName}"
            )

        case None =>
          ZIO.logError(
            s"Critical Error: Winner ID ${cycle.winnerId} not found in Circle ${circle.id}"
          )
      }
    }
  } yield ()
}
