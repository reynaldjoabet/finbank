package coinstar.wallet.persistence

import org.flywaydb.core.Flyway
import zio.*

import javax.sql.DataSource

/** Runs Flyway migrations at startup (fail-fast).
  *
  * In production, you may want:
  *   - a separate "migrate" job in CI/CD,
  *   - or run it on startup only for certain environments.
  *
  * This layer is a *pass-through* DataSource layer: it returns the same
  * DataSource after migrations succeed.
  */
object FlywayMigrate {

  val layer: ZLayer[DataSource, Throwable, DataSource] =
    ZLayer.fromZIO {
      for {
        ds <- ZIO.service[DataSource]
        _ <- ZIO.attempt {
          Flyway
            .configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
          ()
        }
      } yield ds
    }
}
