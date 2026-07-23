package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

final case class PaymentWebhook(
    paymentId: PaymentId,
    status: PaymentStatus,
    settledAtEpochMs: Option[Long]
) derives zio.json.JsonCodec

trait IntegrationService {
  def paymentWebhook(
      req: PaymentWebhook,
      principal: Principal
  ): IO[ApiError, Payment]
}

object IntegrationService {
  val live: URLayer[PaymentService & AuditService, IntegrationService] =
    ZLayer.fromZIO {
      for {
        payments <- ZIO.service[PaymentService]
        audit <- ZIO.service[AuditService]
      } yield new IntegrationService {
        override def paymentWebhook(
            req: PaymentWebhook,
            principal: Principal
        ): IO[ApiError, Payment] = {
          for {
            updated <- payments.setStatus(
              req.paymentId,
              req.status,
              req.settledAtEpochMs
            )
            _ <- audit.record(
              principal,
              "PAYMENT_WEBHOOK",
              "Payment",
              updated.id.value,
              s"status=${updated.status}"
            )
          } yield updated
        }
      }
    }
}
