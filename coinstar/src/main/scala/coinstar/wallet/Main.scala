package coinstar.wallet

import coinstar.wallet.config.AppConfig
import coinstar.wallet.http.RoutesV1
import coinstar.wallet.persistence.*
import coinstar.wallet.service.*
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

/** Coinstar Wallet API (starter) — Scala 3 + ZIO + ZIO HTTP + ZIO Quill.
  *
  * This skeleton focuses on:
  *   - Auth via JWT Bearer token (dev-token endpoint available ONLY in dev)
  *   - Wallet CRUD (minimal)
  *   - Kiosk voucher redeem flow (atomic credit + ledger + voucher mark)
  *   - Idempotency-Key handling (stores successful responses)
  *   - Flyway migrations at startup (fail-fast)
  */
object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val dataSourceWithMigrations =
    Quill.DataSource.fromPrefix("db") >>> FlywayMigrate.layer
  val postgresLayer = Quill.Postgres.fromNamingStrategy(SnakeCase)
  val configLayer = AppConfig.layer
  private val appLayer = {
    ZLayer.make[
      AppConfig & WalletService & RoutesV1 & AuthService & IdempotencyService
    ](
      // 1. Config
      configLayer,

      // 2. Database & Migration
      dataSourceWithMigrations,
      Quill.Postgres.fromNamingStrategy(SnakeCase),

      // 3. Repositories (Dependencies for Services)
      WalletRepoLive.layer,
      LedgerRepoLive.layer,
      VoucherRepoLive.layer,
      IdempotencyRepoLive.layer,

      configLayer >>> AuthServiceLive.layer,
      configLayer >>> WalletServiceLive.layer,
      configLayer >>> IdempotencyServiceLive.layer,

      // 5. HTTP Routes
      RoutesV1.layer
    )
  }

  override def run: ZIO[Any, Throwable, Unit] =
    (for {
      cfg <- ZIO.service[AppConfig]
      routes <- ZIO.service[RoutesV1].map(_.routes)
      _ <- ZIO.logInfo(
        s"Starting server on port ${cfg.http.port} (env=${cfg.env})"
      )
      _ <- Server
        .serve(routes)
        .provide(
          AppConfig.layer,
          Server.defaultWithPort(cfg.http.port),
          WalletRepoLive.layer,
          LedgerRepoLive.layer,
          VoucherRepoLive.layer,
          IdempotencyRepoLive.layer,
          dataSourceWithMigrations,
          Quill.Postgres.fromNamingStrategy(SnakeCase),
          AuthServiceLive.layer,
          WalletServiceLive.layer,
          IdempotencyServiceLive.layer
        )
    } yield ()).provide(AppConfig.layer, RoutesV1.layer)
}
