package coinstar.wallet.service

import coinstar.wallet.config.AppConfig
import coinstar.wallet.domain.{DomainError, Principal, UserId}
import pdi.jwt.{JwtZIOJson, JwtAlgorithm, JwtClaim}
import zio.*
import java.util.UUID
import java.time.Clock
import coinstar.wallet.config.JwtConfig

trait AuthService {
  def verify(token: String): IO[DomainError, Principal]
  def issueDevToken(userId: UserId): UIO[String]
}
object AuthService {
  def verify(token: String): ZIO[AuthService, DomainError, Principal] =
    ZIO.serviceWithZIO[AuthService](_.verify(token))

  def issueDevToken(userId: UserId): ZIO[AuthService, Nothing, String] =
    ZIO.serviceWithZIO[AuthService](_.issueDevToken(userId))
}
final class AuthServiceLive(cfg: AppConfig) extends AuthService {
  private val issuer = cfg.jwt.issuer
  private val key = cfg.jwt.secret

  implicit val clock: Clock = Clock.systemUTC()
  override def verify(token: String): IO[DomainError, Principal] =
    ZIO
      .fromTry(JwtZIOJson.decode(token, "key", Seq(JwtAlgorithm.HS512)))
      .mapError(e => DomainError.Forbidden("Invalid or expired token"))
      .flatMap { claim =>
        if claim.issuer.contains(cfg.jwt.issuer) || cfg.jwt.issuer.isBlank then
          ZIO
            .fromOption(claim.subject)
            .mapError(_ => DomainError.Forbidden("Missing subject claim"))
            .flatMap { sub =>
              ZIO
                .fromEither(UserId.fromString(sub))
                .mapError(DomainError.Forbidden(_))
            }
            .map(Principal(_))
        else ZIO.fail(DomainError.Forbidden("Invalid token issuer"))
      }

  // For local/dev ONLY. In production integrate with OIDC & rotating signing keys.
  override def issueDevToken(userId: UserId): UIO[String] =
    ZIO.succeed {
      val claim = JwtClaim(
        subject = Some(userId.value.toString),
        issuer = Some(cfg.jwt.issuer)
      ).issuedNow(clock).expiresIn(3600)

      JwtZIOJson.encode(claim, cfg.jwt.secret, JwtAlgorithm.HS512)
    }
}

object AuthServiceLive {
  val layer: ZLayer[AppConfig, Nothing, AuthService] =
    ZLayer.fromFunction(new AuthServiceLive(_))
}
