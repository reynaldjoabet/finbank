package services

import domain.*
import zio.*
import zio.stream.*

trait PayoutService {
  def processBulk(
      requests: Chunk[PayoutRequest]
  ): Stream[Throwable, PayoutResult]
}

case class MaviancePayoutLive() extends PayoutService {

  // 2026 Digital Tax Logic: 3% for non-resident platforms
  private val digitalTaxRate = 0.03

  override def processBulk(
      requests: Chunk[PayoutRequest]
  ): Stream[Throwable, PayoutResult] = {
    ZStream
      .fromChunk(requests)
      .mapZIOPar(4) { req => // Process 4 at a time to respect rate limits
        for {
          _ <- ZIO.logInfo(s"Processing Payout for ${req.recipientPhone}")

          // Calculate 3% Tax as per 2026 Finance Law
          tax = req.amount * digitalTaxRate
          net = req.amount - tax

          // Simulate call to Maviance/Tranzak API
          res <- ZIO
            .attempt {
              // In a real app, use sttp/http4s here
              PayoutResult(
                reference = s"CM-${java.util.UUID.randomUUID()}",
                status = "SUCCESS",
                taxDeducted = tax
              )
            }
            .retry(Schedule.exponential(1.second) && Schedule.recurs(3))

        } yield res
      }
  }
}

object MaviancePayout {
  val layer: ULayer[PayoutService] = ZLayer.succeed(MaviancePayoutLive())
}
