package services

import zio.*

trait PlaidService {
  def verifyAccount(email: String): Task[Boolean]
}

case class PlaidServiceLive() extends PlaidService {
  override def verifyAccount(email: String): Task[Boolean] = {
    for {
      _ <- ZIO.logInfo(s"Connecting to Plaid for $email...")
      isValid = true // In production, this calls Plaid /auth/exchange
    } yield isValid
  }
}

object PlaidService {
  val layer: ULayer[PlaidService] = ZLayer.succeed(PlaidServiceLive())
}
