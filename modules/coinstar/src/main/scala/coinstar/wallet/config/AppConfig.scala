package coinstar.wallet.config

import zio._

final case class HttpConfig(port: Int)
final case class JwtConfig(issuer: String, secret: String)
final case class AppConfig(env: String, http: HttpConfig, jwt: JwtConfig)

object AppConfig {

  private def requireEnv(name: String): ZIO[Any, Throwable, String] =
    zio.System.env(name).flatMap {
      case Some(v) if v.trim.nonEmpty => ZIO.succeed(v.trim)
      case _                          =>
        ZIO.fail(
          new RuntimeException(s"Missing required environment variable: $name")
        )
    }

  private def envOrElse(
      name: String,
      default: String
  ): ZIO[Any, Throwable, String] =
    zio.System.env(name).map(_.filter(_.trim.nonEmpty).getOrElse(default))

  private def intEnvOrElse(
      name: String,
      default: Int
  ): ZIO[Any, Throwable, Int] =
    zio.System.env(name).map(_.flatMap(_.toIntOption).getOrElse(default))

  val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.fromZIO {
      for {
        env <- envOrElse("APP_ENV", "dev")
        port <- intEnvOrElse("HTTP_PORT", 8080)

        issuer <- envOrElse("JWT_ISSUER", "coinstar-wallet")
        secret <- zio.System.env("JWT_SECRET").flatMap {
          case Some(s) if s.trim.nonEmpty => ZIO.succeed(s.trim)
          case _ if env == "dev"          => ZIO.succeed("dev-secret-change-me")
          case _                          => requireEnv("JWT_SECRET")
        }
      } yield AppConfig(
        env = env,
        http = HttpConfig(port),
        jwt = JwtConfig(issuer, secret)
      )
    }
}
