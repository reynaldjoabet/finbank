package com.migrantbank.service

import com.migrantbank.config.AppConfig
import com.migrantbank.db.Db
import com.migrantbank.domain.*
import com.migrantbank.repo.*
import com.migrantbank.security.*
import com.migrantbank.ratelimit.RateLimiter
import zio.*

import java.time.Instant
import java.util.UUID

trait AuthService {
  def login(
      phone: String,
      password: String,
      correlationId: String
  ): IO[AppError, AuthTokens]
  def refresh(
      userId: UUID,
      refreshToken: String,
      correlationId: String
  ): IO[AppError, AuthTokens]
  def logout(
      userId: UUID,
      refreshToken: String,
      correlationId: String
  ): IO[AppError, Unit]
}

object AuthService {

  val live: ZLayer[
    Db & AppConfig & PasswordHasher & JwtService & RateLimiter,
    Nothing,
    AuthService
  ] =
    ZLayer.fromFunction {
      (
          db: Db,
          cfg: AppConfig,
          hasher: PasswordHasher,
          jwt: JwtService,
          rl: RateLimiter
      ) =>
        new AuthService {

          private def refreshExpiry(now: Instant): Instant =
            now.plusSeconds(cfg.security.jwt.refreshTokenDays.toLong * 86400L)

          override def login(
              phone: String,
              password: String,
              correlationId: String
          ): IO[AppError, AuthTokens] =
            for {
              _ <- rl.check(s"login:$phone", cfg.rateLimit.loginPerMinute)
              user <- db
                .query {
                  UserRepo.findByPhone(phone)
                }
                .flatMap {
                  case Some(u) => ZIO.succeed(u)
                  case None    =>
                    ZIO.fail(AppError.Unauthorized("Invalid credentials"))
                }
              hash <- ZIO
                .fromOption(user.passwordHash)
                .orElseFail(AppError.Unauthorized("Password not set"))
              ok <- hasher.verify(password, hash)
              _ <- ZIO
                .fail(AppError.Unauthorized("Invalid credentials"))
                .unless(ok)
              access <- jwt.issueAccess(user.id, user.role)
              refreshRaw <- jwt.issueRefresh()
              now <- Clock.instant
              tokenId <- Random.nextUUID
              _ <- db.transaction {
                RefreshTokenRepo.insert(
                  tokenId,
                  user.id,
                  TokenHash.sha256Hex(refreshRaw),
                  refreshExpiry(now)
                )
                AuditRepo.append(
                  "auth_login",
                  Some(user.id),
                  correlationId,
                  "login"
                )
              }
            } yield AuthTokens(access, refreshRaw)

          override def refresh(
              userId: UUID,
              refreshToken: String,
              correlationId: String
          ): IO[AppError, AuthTokens] =
            for {
              now <- Clock.instant
              tokenHash = TokenHash.sha256Hex(refreshToken)
              maybeTokenId <- db.query {
                RefreshTokenRepo.findValid(userId, tokenHash, now)
              }
              tokenId <- ZIO
                .fromOption(maybeTokenId)
                .orElseFail(AppError.Unauthorized("Invalid refresh token"))
              user <- db.query { UserRepo.findById(userId) }.flatMap {
                case Some(u) => ZIO.succeed(u)
                case None    =>
                  ZIO.fail(AppError.NotFound(s"User $userId not found"))
              }
              access <- jwt.issueAccess(userId, user.role)
              newRefresh <- jwt.issueRefresh()
              newTokenId <- Random.nextUUID
              _ <- db.transaction {
                RefreshTokenRepo.revoke(tokenId)
                RefreshTokenRepo.insert(
                  newTokenId,
                  userId,
                  TokenHash.sha256Hex(newRefresh),
                  refreshExpiry(now)
                )
                AuditRepo.append(
                  "auth_refresh",
                  Some(userId),
                  correlationId,
                  "refresh rotated"
                )
              }
            } yield AuthTokens(access, newRefresh)

          override def logout(
              userId: UUID,
              refreshToken: String,
              correlationId: String
          ): IO[AppError, Unit] =
            for {
              now <- Clock.instant
              tokenHash = TokenHash.sha256Hex(refreshToken)
              maybeTokenId <- db.query {
                RefreshTokenRepo.findValid(userId, tokenHash, now)
              }
              _ <- maybeTokenId match {
                case Some(id) =>
                  db.transaction {
                    RefreshTokenRepo.revoke(id)
                    AuditRepo.append(
                      "auth_logout",
                      Some(userId),
                      correlationId,
                      "logout"
                    )
                  }
                case None =>
                  ZIO.unit
              }
            } yield ()
        }
    }
}
