package migrantbank.security

import migrantbank.config.AppConfig
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import com.nimbusds.jwt.*
import migrantbank.domain.AppError
import migrantbank.domain.AuthContext
import zio.*

import java.util.Date
import java.util.UUID

trait JwtService2 {
  def issueAccess(userId: UUID, role: String): UIO[String]
  def issueRefresh(): UIO[String]
  def verifyAccess(token: String): IO[AppError.Unauthorized, AuthContext]
}

object JwtService2 {

  val live: ZLayer[AppConfig, Nothing, JwtService2] =
    ZLayer.fromFunction { (cfg: AppConfig) =>
      new JwtService2 {
        private val sharedSecret = cfg.security.jwt.secret.getBytes
        private val signer = new MACSigner(sharedSecret)
        private val verifier = new MACVerifier(sharedSecret)

        private def nowMs: Long = java.lang.System.currentTimeMillis()

        override def issueAccess(userId: UUID, role: String): UIO[String] =
          ZIO.succeed {
            val expirationTime = new Date(
              nowMs + cfg.security.jwt.accessTokenMinutes.toLong * 60000L
            )

            val claimsSet = new JWTClaimsSet.Builder()
              .issuer(cfg.security.jwt.issuer)
              .subject(userId.toString)
              .claim("role", role)
              .expirationTime(expirationTime)
              .build()

            val signedJWT = new SignedJWT(
              new JWSHeader(JWSAlgorithm.HS256),
              claimsSet
            )

            signedJWT.sign(signer)
            signedJWT.serialize()
          }

        override def issueRefresh(): UIO[String] =
          for {
            u1 <- Random.nextUUID
            u2 <- Random.nextUUID
          } yield s"$u1-$u2"

        override def verifyAccess(
            token: String
        ): IO[AppError.Unauthorized, AuthContext] =
          ZIO
            .attempt {
              val signedJWT = SignedJWT.parse(token)

              // 1. Verify Signature
              if (!signedJWT.verify(verifier))
                throw new Exception("Invalid Signature")

              val claims = signedJWT.getJWTClaimsSet

              // 2. Check Expiration
              val now = new Date()
              val exp = claims.getExpirationTime
              if (exp == null || now.after(exp))
                throw new Exception("Token Expired")

              // 3. Check Issuer
              if (claims.getIssuer != cfg.security.jwt.issuer)
                throw new Exception("Invalid Issuer")

              val userId = UUID.fromString(claims.getSubject)
              val role =
                scala.Option(claims.getStringClaim("role")).getOrElse("user")

              AuthContext(userId, role)
            }
            .mapError(_ => AppError.Unauthorized("Invalid or expired token"))
      }
    }
}
