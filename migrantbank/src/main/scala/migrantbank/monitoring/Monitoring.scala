package com.migrantbank.monitoring

import com.migrantbank.config.AppConfig
import com.migrantbank.integrations.*
import zio.*

trait Monitoring {
  def start: UIO[Unit]
}
object Monitoring {

  val live: ZLayer[
    AppConfig & AlloyClient & VouchedClient & MbanqClient & SmsClient &
      EmailClient,
    Nothing,
    Monitoring
  ] =
    ZLayer.fromFunction {
      (
          cfg: AppConfig,
          alloy: AlloyClient,
          vouched: VouchedClient,
          mb: MbanqClient,
          sms: SmsClient,
          email: EmailClient
      ) =>
        new Monitoring {

          private def check(name: String, health: UIO[ProviderHealth]) =
            health.flatMap {
              case ProviderHealth.Healthy =>
                ZIO.logDebug(s"[monitor] $name healthy")
              case ProviderHealth.Unhealthy(reason) =>
                val msg = s"$name UNHEALTHY: $reason"
                ZIO.logWarning(msg) *>
                  email.send(
                    cfg.monitoring.alertEmail,
                    s"[ALERT] $name unhealthy",
                    msg
                  ) *>
                  sms.send(cfg.monitoring.alertPhone, msg)
            }

          override def start: UIO[Unit] =
            (for {
              _ <- check("Alloy", alloy.health)
              _ <- check("Vouched", vouched.health)
              _ <- check("Mbanq", mb.health)
            } yield ())
              .repeat(Schedule.spaced(cfg.monitoring.intervalSeconds.seconds))
              .unit
        }
    }
}
