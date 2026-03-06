package revenue

import zio.*
import zio.http.*
import zio.config.magnolia.*
import zio.config.typesafe.TypesafeConfigProvider
import revenue.domain.JwtConfig
import revenue.repo.inmemory.*
import revenue.service.*
import revenue.api.AppRoutes

final case class HttpConfig(host: String, port: Int) derives Config
final case class AppConfig(http: HttpConfig, jwt: JwtCfg) derives Config
final case class JwtCfg(
    issuer: String,
    audience: String,
    hmacSecret: String,
    accessTtlSeconds: Long,
    refreshTtlSeconds: Long
) derives Config

object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private val serverConfigLayer: ZLayer[Any, zio.Config.Error, Server.Config] =
    ZLayer.fromZIO {
      ZIO.config[AppConfig].map { cfg =>
        Server.Config.default.binding(cfg.http.host, cfg.http.port)
      }
    }

  private val jwtConfigLayer: ZLayer[Any, zio.Config.Error, JwtConfig] =
    ZLayer.fromZIO {
      ZIO.config[AppConfig].map { cfg =>
        JwtConfig(
          issuer = cfg.jwt.issuer,
          audience = cfg.jwt.audience,
          hmacSecret = cfg.jwt.hmacSecret,
          accessTtlSeconds = cfg.jwt.accessTtlSeconds,
          refreshTtlSeconds = cfg.jwt.refreshTtlSeconds
        )
      }
    }

  private val appLayer =
    ZLayer.make[AppRoutes.Env](
      // repos
      InMemoryTaxpayerRepo.layer,
      InMemoryReturnRepo.layer,
      InMemoryRiskRuleRepo.layer,
      InMemoryAssessmentRepo.layer,
      InMemoryPaymentRepo.layer,
      InMemoryRefundRepo.layer,
      InMemoryObjectionRepo.layer,
      InMemoryCaseRepo.layer,
      InMemoryDocumentRepo.layer,
      InMemoryAuditRepo.layer,
      InMemoryUserRepo.layer,
      InMemoryRefreshTokenRepo.layer,

      // infra
      jwtConfigLayer,
      ZLayer.succeed(Clock.ClockLive),

      // services
      AuditService.live,
      AuthService.live,
      TaxpayerService.live,
      RiskRuleService.live,
      DocumentService.live,
      ReturnService.live,
      AssessmentService.live,
      PaymentService.live,
      RefundService.live,
      ObjectionService.live,
      CaseService.live,
      IntegrationService.live
    )

  override def run: ZIO[Any, Throwable, Unit] = {
    Server
      .serve(AppRoutes.routes)
      .provide(
        serverConfigLayer,
        Server.live,
        appLayer
      )
  }
}
