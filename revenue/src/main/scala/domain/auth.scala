package revenue.domain

import zio.json.*
import revenue.domain.ids.*

final case class LoginRequest(
    username: String,
    password: String,
    actingTaxpayerId: Option[TaxpayerId]
) derives JsonCodec

final case class TokenPair(
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long
) derives JsonCodec

final case class RefreshRequest(
    refreshToken: String
) derives JsonCodec

final case class LogoutRequest(
    refreshToken: String
) derives JsonCodec

final case class ForgotPasswordRequest(
    usernameOrEmail: String
) derives JsonCodec

final case class ResetPasswordRequest(
    resetToken: String,
    newPassword: String
) derives JsonCodec

final case class User(
    id: UserId,
    username: String,
    passwordHash: String,
    roles: Set[Role],
    defaultTaxpayerId: Option[TaxpayerId]
) derives JsonCodec

final case class RefreshTokenRecord(
    id: RefreshTokenId,
    userId: UserId,
    token: String,
    expiresAtEpochMs: Long,
    revoked: Boolean,
    createdAtEpochMs: Long
) derives JsonCodec

final case class JwtConfig(
    issuer: String,
    audience: String,
    hmacSecret: String,
    accessTtlSeconds: Long,
    refreshTtlSeconds: Long
) derives JsonCodec
