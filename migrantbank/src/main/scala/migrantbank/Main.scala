package migrantbank

import migrantbank.api.AppRoutes
import migrantbank.config.{AppConfig, ConfigLoader}
import migrantbank.db.Db
import migrantbank.integrations.DummyProviders
import migrantbank.metrics.Metrics
import migrantbank.monitoring.Monitoring
import migrantbank.ratelimit.RateLimiter
import migrantbank.security.{Crypto, JwtService, PasswordHasher}
import migrantbank.service.*
import zio.*
import zio.http.*

object Main extends ZIOAppDefault {

  override def run =
    for {
      cfg <- ConfigLoader.load
      _ <- ZIO.logInfo(
        s"Starting MigrantBank backend on ${cfg.http.host}:${cfg.http.port}"
      )
      appLayer = ZLayer.make[AppRoutes.Env & Monitoring](
        ZLayer.succeed(cfg),
        Db.live,
        PasswordHasher.live,
        Crypto.live,
        JwtService.live,
        Metrics.live,
        RateLimiter.live,
        DummyProviders.layer,
        // services
        RegistrationService.live,
        AuthService.live,
        AccountService.live,
        FundingService.live,
        TransferService.live,
        FamilyService.live,
        PaycheckService.live,
        LoanService.live,
        SupportService.live,
        AdminService.live,
        CardService.live,
        // monitoring
        Monitoring.live
      )
      _ <- (ZIO.serviceWithZIO[Monitoring](_.start).forkDaemon *> Server.serve(
        AppRoutes.routes
      ))
        .provide(appLayer, Server.defaultWithPort(cfg.http.port))
    } yield ()
}
