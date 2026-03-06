package revenue.service

import zio.*
import zio.json.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}

import java.util.{Base64, Date}
import java.security.{MessageDigest, SecureRandom}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

trait AuthService {
  def authenticateAccessToken(token: String): IO[ApiError, Principal]

  def login(req: LoginRequest): IO[ApiError, TokenPair]
  def refresh(req: RefreshRequest): IO[ApiError, TokenPair]
  def logout(req: LogoutRequest): IO[ApiError, Unit]

  def forgotPassword(req: ForgotPasswordRequest): IO[ApiError, Unit]
  def resetPassword(req: ResetPasswordRequest): IO[ApiError, Unit]
}

object AuthService {

  // PBKDF2 password hashing helper (JDK only). Store as "pbkdf2$iters$saltB64$hashB64"
  object Passwords {

    private val rng = new SecureRandom()

    def hash(
        password: String,
        iters: Int = 120_000,
        saltLen: Int = 16,
        dkLenBits: Int = 256
    ): String = {
      val salt = Array.ofDim[Byte](saltLen)
      rng.nextBytes(salt)
      val spec = new PBEKeySpec(password.toCharArray, salt, iters, dkLenBits)
      val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      val dk = skf.generateSecret(spec).getEncoded
      val saltB64 = Base64.getEncoder.encodeToString(salt)
      val dkB64 = Base64.getEncoder.encodeToString(dk)
      s"pbkdf2$$$iters$$$saltB64$$$dkB64"
    }

    def verify(password: String, stored: String): Boolean = {
      val parts = stored.split("\\$")
      if (parts.length != 4) { false }
      else {
        val iters = parts(1).toInt
        val salt = Base64.getDecoder.decode(parts(2))
        val hash = Base64.getDecoder.decode(parts(3))
        val spec =
          new PBEKeySpec(password.toCharArray, salt, iters, hash.length * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val dk = skf.generateSecret(spec).getEncoded
        MessageDigest.isEqual(dk, hash)
      }
    }
  }

  private def nowMs(clock: Clock): UIO[Long] =
    clock.instant.map(_.toEpochMilli)

  private def issueAccessToken(
      cfg: JwtConfig,
      user: User,
      actingTaxpayerId: Option[TaxpayerId],
      clock: Clock
  ): UIO[(String, Long)] = {
    for {
      now <- nowMs(clock)
      exp = now + cfg.accessTtlSeconds * 1000L
      claims = new JWTClaimsSet.Builder()
        .issuer(cfg.issuer)
        .audience(cfg.audience)
        .subject(user.username)
        .expirationTime(new Date(exp))
        .issueTime(new Date(now))
        .claim("roles", user.roles.map(_.toString).mkString(","))
        .claim(
          "taxpayerId",
          actingTaxpayerId.orElse(user.defaultTaxpayerId).map(_.value).orNull
        )
        .build()

      header = new JWSHeader(JWSAlgorithm.HS256)
      jwt = new SignedJWT(header, claims)
      _ <- ZIO
        .attempt(jwt.sign(new MACSigner(cfg.hmacSecret.getBytes("UTF-8"))))
        .orDie
    } yield (jwt.serialize(), cfg.accessTtlSeconds)
  }

  private def verifyAccessToken(
      cfg: JwtConfig,
      token: String
  ): IO[ApiError, Principal] = {
    for {
      jwt <- ZIO
        .attempt(SignedJWT.parse(token))
        .mapError(_ => ApiError.Unauthorized("Invalid JWT"))
      ok <- ZIO
        .attempt(jwt.verify(new MACVerifier(cfg.hmacSecret.getBytes("UTF-8"))))
        .mapError(_ => ApiError.Unauthorized("JWT verify failed"))
      _ <- ZIO.fail(ApiError.Unauthorized("Invalid JWT signature")).unless(ok)

      claims <- ZIO
        .attempt(jwt.getJWTClaimsSet)
        .mapError(_ => ApiError.Unauthorized("Invalid claims"))
      _ <- ZIO
        .fail(ApiError.Unauthorized("Invalid issuer"))
        .unless(claims.getIssuer == cfg.issuer)
      audOk = Option(claims.getAudience).exists(_.contains(cfg.audience))
      _ <- ZIO.fail(ApiError.Unauthorized("Invalid audience")).unless(audOk)
      expOk = Option(claims.getExpirationTime).exists(
        _.getTime > java.lang.System.currentTimeMillis()
      )
      _ <- ZIO.fail(ApiError.Unauthorized("Token expired")).unless(expOk)

      roles = Option(claims.getStringClaim("roles")).toList
        .flatMap(_.split(",").toList.map(_.trim).filter(_.nonEmpty))
        .flatMap(s => Role.values.find(_.toString == s))
        .toSet

      actingTaxpayerId = Option(claims.getStringClaim("taxpayerId"))
        .filter(_ != null)
        .map(TaxpayerId(_))
      subject = Option(claims.getSubject).getOrElse("unknown")
    } yield Principal(subject, roles, actingTaxpayerId)
  }

  val live
      : URLayer[UserRepo & RefreshTokenRepo & Clock & JwtConfig, AuthService] =
    ZLayer.fromZIO {
      for {
        users <- ZIO.service[UserRepo]
        refreshRepo <- ZIO.service[RefreshTokenRepo]
        clock <- ZIO.service[Clock]
        cfg <- ZIO.service[JwtConfig]
      } yield new AuthService {

        override def authenticateAccessToken(
            token: String
        ): IO[ApiError, Principal] =
          verifyAccessToken(cfg, token)

        override def login(req: LoginRequest): IO[ApiError, TokenPair] = {
          for {
            uOpt <- users
              .findByUsername(req.username)
              .mapError(ApiError.fromRepo)
            user <- ZIO
              .fromOption(uOpt)
              .orElseFail(ApiError.Unauthorized("Invalid username or password"))

            // NOTE: In InMemoryUserRepo we used "HASH_ME" placeholders; replace them with Passwords.hash("...") values.
            _ <- ZIO
              .fail(
                ApiError.Unauthorized(
                  "User password hash not configured (replace HASH_ME)"
                )
              )
              .when(user.passwordHash == "HASH_ME")

            ok <- ZIO.succeed(Passwords.verify(req.password, user.passwordHash))
            _ <- ZIO
              .fail(ApiError.Unauthorized("Invalid username or password"))
              .unless(ok)

            pair <- issueAccessToken(
              cfg,
              user,
              req.actingTaxpayerId,
              clock
            )
            access = pair._1
            ttl = pair._2

            now <- nowMs(clock)
            refreshTokenString <- Random.nextUUID.map(_.toString)
            refreshId <- Random.nextUUID.map(u => RefreshTokenId(u.toString))
            refreshRecord = RefreshTokenRecord(
              id = refreshId,
              userId = user.id,
              token = refreshTokenString,
              expiresAtEpochMs = now + cfg.refreshTtlSeconds * 1000L,
              revoked = false,
              createdAtEpochMs = now
            )
            _ <- refreshRepo.create(refreshRecord).mapError(ApiError.fromRepo)
          } yield TokenPair(access, refreshTokenString, ttl)
        }

        override def refresh(req: RefreshRequest): IO[ApiError, TokenPair] = {
          for {
            recOpt <- refreshRepo
              .findByToken(req.refreshToken)
              .mapError(ApiError.fromRepo)
            rec <- ZIO
              .fromOption(recOpt)
              .orElseFail(ApiError.Unauthorized("Invalid refresh token"))
            _ <- ZIO
              .fail(ApiError.Unauthorized("Refresh token revoked"))
              .when(rec.revoked)

            now <- nowMs(clock)
            _ <- ZIO
              .fail(ApiError.Unauthorized("Refresh token expired"))
              .when(rec.expiresAtEpochMs <= now)

            userOpt <- users.get(rec.userId).mapError(ApiError.fromRepo)
            user <- ZIO
              .fromOption(userOpt)
              .orElseFail(ApiError.Unauthorized("Unknown user"))

            pair2 <- issueAccessToken(
              cfg,
              user,
              user.defaultTaxpayerId,
              clock
            )
          } yield TokenPair(pair2._1, rec.token, pair2._2)
        }

        override def logout(req: LogoutRequest): IO[ApiError, Unit] = {
          for {
            recOpt <- refreshRepo
              .findByToken(req.refreshToken)
              .mapError(ApiError.fromRepo)
            rec <- ZIO
              .fromOption(recOpt)
              .orElseFail(ApiError.NotFound("Refresh token not found"))
            _ <- refreshRepo.revoke(rec.id).mapError(ApiError.fromRepo)
          } yield ()
        }

        override def forgotPassword(
            req: ForgotPasswordRequest
        ): IO[ApiError, Unit] =
          // In production: create reset token, send email/SMS, rate limit.
          ZIO.unit

        override def resetPassword(
            req: ResetPasswordRequest
        ): IO[ApiError, Unit] =
          // In production: verify reset token, set new hash, revoke sessions.
          ZIO.unit
      }
    }
}
