package migrantbank.config

final case class AppConfig(
    http: AppConfig.Http,
    db: AppConfig.Db,
    security: AppConfig.Security,
    fraud: AppConfig.Fraud,
    rateLimit: AppConfig.RateLimit,
    monitoring: AppConfig.Monitoring
)

object AppConfig {

  final case class Http(host: String, port: Int)
  final case class Db(
      jdbcUrl: String,
      user: String,
      password: String,
      maxPoolSize: Int
  )

  final case class Security(
      encryptionKeyBase64: String,
      smsCodeTtlMinutes: Int,
      jwt: Jwt
  )

  final case class Jwt(
      issuer: String,
      secret: String,
      accessTokenMinutes: Int,
      refreshTokenDays: Int
  )

  final case class Fraud(flagThresholdMinor: Long)

  final case class RateLimit(
      requestsPerMinute: Int,
      loginPerMinute: Int
  )

  final case class Monitoring(
      intervalSeconds: Int,
      alertEmail: String,
      alertPhone: String
  )
}
