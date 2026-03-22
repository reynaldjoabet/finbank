package com.migrantbank.integrations
import com.migrantbank.domain.*
import zio.*

enum ProviderHealth derives CanEqual {
  case Healthy
  case Unhealthy(reason: String)
}
trait AlloyClient {
  def runKyc(profile: UserProfile): IO[AppError.ProviderUnavailable, KycStatus]
  def health: UIO[ProviderHealth]
}
trait VouchedClient {
  def verifyIdentity(
      profile: UserProfile
  ): IO[AppError.ProviderUnavailable, Boolean]
  def health: UIO[ProviderHealth]
}
trait MbanqClient {
  def issueVirtualCard(
      userId: java.util.UUID
  ): IO[AppError.ProviderUnavailable, Card]
  def orderPhysicalCard(
      userId: java.util.UUID
  ): IO[AppError.ProviderUnavailable, Card]
  def health: UIO[ProviderHealth]
}
trait SmsClient {
  def send(to: String, message: String): UIO[Unit]
}
trait EmailClient {
  def send(to: String, subject: String, body: String): UIO[Unit]
}
object DummyProviders {

  private class DummyImpl
      extends AlloyClient
      with VouchedClient
      with MbanqClient
      with SmsClient
      with EmailClient {

    override def runKyc(
        profile: UserProfile
    ): IO[AppError.ProviderUnavailable, KycStatus] =
      ZIO.succeed(KycStatus.VERIFIED)

    override def verifyIdentity(
        profile: UserProfile
    ): IO[AppError.ProviderUnavailable, Boolean] =
      ZIO.succeed(true)

    override def issueVirtualCard(
        userId: java.util.UUID
    ): IO[AppError.ProviderUnavailable, Card] =
      for {
        id <- Random.nextUUID
        now <- Clock.instant
        last4 <- Random.nextIntBetween(0, 10000).map(n => f"$n%04d")
      } yield Card(
        id,
        userId,
        CardKind.VIRTUAL,
        last4,
        CardStatus.ACTIVE,
        DeliveryStatus.NOT_ORDERED,
        now
      )

    override def orderPhysicalCard(
        userId: java.util.UUID
    ): IO[AppError.ProviderUnavailable, Card] =
      for {
        id <- Random.nextUUID
        now <- Clock.instant
        last4 <- Random.nextIntBetween(0, 10000).map(n => f"$n%04d")
      } yield Card(
        id,
        userId,
        CardKind.PHYSICAL,
        last4,
        CardStatus.ACTIVE,
        DeliveryStatus.ORDERED,
        now
      )

    override def send(to: String, message: String): UIO[Unit] =
      ZIO.logInfo(s"[SMS to=$to] $message").unit

    override def send(
        to: String,
        subject: String,
        body: String
    ): UIO[Unit] =
      ZIO.logInfo(s"[EMAIL to=$to subject=$subject] $body").unit

    override def health: UIO[ProviderHealth] =
      ZIO.succeed(ProviderHealth.Healthy)
  }

  private val impl = new DummyImpl

  val alloyLayer: ZLayer[Any, Nothing, AlloyClient] =
    ZLayer.succeed[AlloyClient](impl)

  val vouchedLayer: ZLayer[Any, Nothing, VouchedClient] =
    ZLayer.succeed[VouchedClient](impl)

  val mbanqLayer: ZLayer[Any, Nothing, MbanqClient] =
    ZLayer.succeed[MbanqClient](impl)

  val smsLayer: ZLayer[Any, Nothing, SmsClient] =
    ZLayer.succeed[SmsClient](impl)

  val emailLayer: ZLayer[Any, Nothing, EmailClient] =
    ZLayer.succeed[EmailClient](impl)

  /** Replace with real HTTP clients in production. */
  val layer: ZLayer[
    Any,
    Nothing,
    AlloyClient & VouchedClient & MbanqClient & SmsClient & EmailClient
  ] =
    alloyLayer ++ vouchedLayer ++ mbanqLayer ++ smsLayer ++ emailLayer
}
