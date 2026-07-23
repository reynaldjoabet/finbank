package migrantbank.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import migrantbank.config.AppConfig
import migrantbank.domain.AppError
import migrantbank.domain.AuthContext
import zio.*

import java.util.Date
import java.util.UUID

trait JwtService {
  def issueAccess(userId: UUID, role: String): UIO[String]
  def issueRefresh(): UIO[String]
  def verifyAccess(token: String): IO[AppError.Unauthorized, AuthContext]
}
object JwtService {

  val live: ZLayer[AppConfig, Nothing, JwtService] =
    ZLayer.fromFunction { (cfg: AppConfig) =>
      new JwtService {
        private val algo = Algorithm.HMAC256(cfg.security.jwt.secret)
        private def nowMs = java.lang.System.currentTimeMillis()

        override def issueAccess(userId: UUID, role: String): UIO[String] =
          ZIO.succeed {
            val exp = new Date(
              nowMs + cfg.security.jwt.accessTokenMinutes.toLong * 60000L
            )
            JWT
              .create()
              .withIssuer(cfg.security.jwt.issuer)
              .withSubject(userId.toString)
              .withClaim("role", role)
              .withExpiresAt(exp)
              .sign(algo)
          }

        override def issueRefresh(): UIO[String] =
          ZIO.succeed(
            java.util.UUID.randomUUID().toString + "-" + java.util.UUID
              .randomUUID()
              .toString
          )

        override def verifyAccess(
            token: String
        ): IO[AppError.Unauthorized, AuthContext] =
          ZIO
            .attempt {
              val verifier =
                JWT.require(algo).withIssuer(cfg.security.jwt.issuer).build()
              val decoded = verifier.verify(token)
              val userId = UUID.fromString(decoded.getSubject)
              val role = Option(decoded.getClaim("role"))
                .map(_.asString())
                .getOrElse("user")
              AuthContext(userId, role)
            }
            .mapError(_ => AppError.Unauthorized("Invalid or expired token"))
      }

    }
}
