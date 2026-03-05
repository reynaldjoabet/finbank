package com.migrantbank

import com.migrantbank.api.AppRoutes
import com.migrantbank.config.{AppConfig, ConfigLoader}
import com.migrantbank.db.Db
import com.migrantbank.integrations.DummyProviders
import com.migrantbank.metrics.Metrics
import com.migrantbank.monitoring.Monitoring
import com.migrantbank.ratelimit.RateLimiter
import com.migrantbank.security.{Crypto, JwtService, PasswordHasher}
import com.migrantbank.service.*
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
