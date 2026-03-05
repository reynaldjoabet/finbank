package com.migrantbank.db

import com.augustnagro.magnum.*
import com.migrantbank.config.AppConfig
import com.migrantbank.domain.AppError
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import zio.*
import javax.sql.DataSource

trait Db {
  def transactor: UIO[Transactor]
  def query[A](f: DbCon ?=> A): IO[AppError, A]
  def transaction[A](f: DbCon ?=> A): IO[AppError, A]
  def dataSource: UIO[DataSource]
}
object Db {

  private def toAppError(t: Throwable): AppError =
    AppError.Internal("Database error", Some(t))

  val live: ZLayer[AppConfig, Throwable, Db] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[AppConfig]
        ds <- ZIO.acquireRelease(
          ZIO.attempt {
            val hc = new HikariConfig()
            hc.setJdbcUrl(cfg.db.jdbcUrl)
            hc.setUsername(cfg.db.user)
            hc.setPassword(cfg.db.password)
            hc.setMaximumPoolSize(cfg.db.maxPoolSize)
            hc.setPoolName("migrantbank-hikari")
            new HikariDataSource(hc)
          }
        )(ds => ZIO.attempt(ds.close()).orDie)

        // Flyway migration remains the same
        _ <- ZIO
          .attempt {
            Flyway
              .configure()
              .dataSource(ds)
              .locations("classpath:db/migration")
              .load()
              .migrate()
          }
          .tapError(e =>
            ZIO.logError(s"Flyway migration failed: ${e.getMessage}")
          )

        // Create the Magnum Transactor
        xa = Transactor(ds)

      } yield new Db {
        override val dataSource: UIO[DataSource] = ZIO.succeed(ds)
        override val transactor: UIO[Transactor] = ZIO.succeed(xa)

        override def query[A](f: DbCon ?=> A): IO[AppError, A] =
          ZIO.attempt(xa.connect(f)).mapError(toAppError)

        override def transaction[A](f: DbCon ?=> A): IO[AppError, A] =
          ZIO.attempt(xa.transact(f)).mapError(toAppError)
      }
    }
}
