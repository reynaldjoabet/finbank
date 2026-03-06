package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait PaymentService {
  def createIntent(
      req: PaymentIntentCreate,
      principal: Principal
  ): IO[ApiError, Payment]
  def get(id: PaymentId, principal: Principal): IO[ApiError, Payment]
  def listByTaxpayer(
      taxpayerId: TaxpayerId,
      principal: Principal
  ): IO[ApiError, List[Payment]]
  def confirm(
      id: PaymentId,
      req: PaymentConfirm,
      principal: Principal
  ): IO[ApiError, (Payment, Receipt)]
  def receipt(paymentId: PaymentId, principal: Principal): IO[ApiError, Receipt]

  // called by integrations/webhook
  def setStatus(
      paymentId: PaymentId,
      status: PaymentStatus,
      settledAtMs: Option[Long]
  ): IO[ApiError, Payment]
}

object PaymentService {

  val live: URLayer[
    PaymentRepo & AssessmentRepo & AuditService & Clock,
    PaymentService
  ] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[PaymentRepo]
        assessments <- ZIO.service[AssessmentRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new PaymentService {

        override def createIntent(
            req: PaymentIntentCreate,
            principal: Principal
        ): IO[ApiError, Payment] = {
          for {
            lOpt <- assessments
              .getLiability(req.liabilityId)
              .mapError(ApiError.fromRepo)
            l <- ZIO
              .fromOption(lOpt)
              .orElseFail(
                ApiError.NotFound(
                  s"Liability not found: ${req.liabilityId.value}"
                )
              )
            _ <- ZIO
              .fail(ApiError.BadRequest("Liability is not open"))
              .unless(l.status == LiabilityStatus.Open)

            now <- clock.instant.map(_.toEpochMilli)
            pid <- Random.nextUUID.map(u => PaymentId(u.toString))

            amount = req.amount.getOrElse(l.amount)
            currency = req.currency.getOrElse(l.currency)

            p = Payment(
              pid,
              req.taxpayerId,
              req.liabilityId,
              req.method,
              amount,
              currency,
              PaymentStatus.Pending,
              now,
              None
            )
            saved <- repo.create(p).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "PAYMENT_INTENT_CREATED",
              "Payment",
              saved.id.value,
              s"liability=${l.id.value} amount=${saved.amount}"
            )
          } yield saved
        }

        override def get(
            id: PaymentId,
            principal: Principal
        ): IO[ApiError, Payment] =
          repo.get(id).mapError(ApiError.fromRepo).flatMap { opt =>
            ZIO
              .fromOption(opt)
              .orElseFail(ApiError.NotFound(s"Payment not found: ${id.value}"))
          }

        override def listByTaxpayer(
            taxpayerId: TaxpayerId,
            principal: Principal
        ): IO[ApiError, List[Payment]] =
          repo.listByTaxpayer(taxpayerId).mapError(ApiError.fromRepo)

        override def confirm(
            id: PaymentId,
            req: PaymentConfirm,
            principal: Principal
        ): IO[ApiError, (Payment, Receipt)] = {
          for {
            p <- get(id, principal)
            _ <- ZIO
              .fail(ApiError.BadRequest("Payment already finalized"))
              .when(p.status != PaymentStatus.Pending)

            now <- clock.instant.map(_.toEpochMilli)
            updated <- setStatus(id, PaymentStatus.Settled, Some(now))

            // Mark liability paid if settled
            lOpt <- assessments
              .getLiability(updated.liabilityId)
              .mapError(ApiError.fromRepo)
            l <- ZIO
              .fromOption(lOpt)
              .orElseFail(ApiError.NotFound("Liability for payment not found"))
            _ <- assessments
              .updateLiability(l.copy(status = LiabilityStatus.Paid))
              .mapError(ApiError.fromRepo)

            rid <- Random.nextUUID.map(u => ReceiptId(u.toString))
            receipt = Receipt(
              rid,
              updated.id,
              now,
              reference = s"RCPT-${rid.value.take(8).toUpperCase}"
            )
            savedReceipt <- repo
              .upsertReceipt(receipt)
              .mapError(ApiError.fromRepo)

            _ <- audit.record(
              principal,
              "PAYMENT_CONFIRMED",
              "Payment",
              updated.id.value,
              s"receipt=${receipt.reference}"
            )
          } yield (updated, savedReceipt)
        }

        override def receipt(
            paymentId: PaymentId,
            principal: Principal
        ): IO[ApiError, Receipt] =
          repo
            .getReceiptByPayment(paymentId)
            .mapError(ApiError.fromRepo)
            .flatMap { opt =>
              ZIO
                .fromOption(opt)
                .orElseFail(
                  ApiError.NotFound(
                    s"Receipt not found for payment: ${paymentId.value}"
                  )
                )
            }

        override def setStatus(
            paymentId: PaymentId,
            status: PaymentStatus,
            settledAtMs: Option[Long]
        ): IO[ApiError, Payment] = {
          for {
            pOpt <- repo.get(paymentId).mapError(ApiError.fromRepo)
            p <- ZIO
              .fromOption(pOpt)
              .orElseFail(
                ApiError.NotFound(s"Payment not found: ${paymentId.value}")
              )
            next = p.copy(
              status = status,
              settledAtEpochMs = settledAtMs.orElse(p.settledAtEpochMs)
            )
            _ <- repo.update(next).mapError(ApiError.fromRepo)
          } yield next
        }
      }
    }
}
