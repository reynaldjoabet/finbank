package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait RefundService {
  def create(
      req: RefundClaimCreate,
      principal: Principal
  ): IO[ApiError, RefundClaim]
  def get(id: RefundId, principal: Principal): IO[ApiError, RefundClaim]
  def listByTaxpayer(
      taxpayerId: TaxpayerId,
      principal: Principal
  ): IO[ApiError, List[RefundClaim]]
  def approve(
      id: RefundId,
      decision: RefundDecision,
      principal: Principal
  ): IO[ApiError, RefundClaim]
  def reject(
      id: RefundId,
      decision: RefundDecision,
      principal: Principal
  ): IO[ApiError, RefundClaim]
}

object RefundService {

  val live: URLayer[RefundRepo & AuditService & Clock, RefundService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[RefundRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new RefundService {

        override def create(
            req: RefundClaimCreate,
            principal: Principal
        ): IO[ApiError, RefundClaim] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => RefundId(u.toString))
            claim = RefundClaim(
              id = id,
              taxpayerId = req.taxpayerId,
              taxType = req.taxType,
              period = req.period,
              amount = req.amount,
              currency = req.currency,
              reason = req.reason,
              status = RefundStatus.Submitted,
              createdAtEpochMs = now,
              updatedAtEpochMs = now
            )
            saved <- repo.create(claim).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "REFUND_CLAIM_SUBMITTED",
              "Refund",
              saved.id.value,
              s"${saved.amount} ${saved.currency}"
            )
          } yield saved
        }

        override def get(
            id: RefundId,
            principal: Principal
        ): IO[ApiError, RefundClaim] =
          repo
            .get(id)
            .mapError(ApiError.fromRepo)
            .flatMap(opt =>
              ZIO
                .fromOption(opt)
                .orElseFail(ApiError.NotFound(s"Refund not found: ${id.value}"))
            )

        override def listByTaxpayer(
            taxpayerId: TaxpayerId,
            principal: Principal
        ): IO[ApiError, List[RefundClaim]] =
          repo.listByTaxpayer(taxpayerId).mapError(ApiError.fromRepo)

        override def approve(
            id: RefundId,
            decision: RefundDecision,
            principal: Principal
        ): IO[ApiError, RefundClaim] = {
          for {
            prev <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            next <- repo
              .update(
                prev
                  .copy(status = RefundStatus.Approved, updatedAtEpochMs = now)
              )
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "REFUND_APPROVED",
              "Refund",
              next.id.value,
              decision.reason.getOrElse("-")
            )
          } yield next
        }

        override def reject(
            id: RefundId,
            decision: RefundDecision,
            principal: Principal
        ): IO[ApiError, RefundClaim] = {
          for {
            prev <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            next <- repo
              .update(
                prev
                  .copy(status = RefundStatus.Rejected, updatedAtEpochMs = now)
              )
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "REFUND_REJECTED",
              "Refund",
              next.id.value,
              decision.reason.getOrElse("-")
            )
          } yield next
        }
      }
    }
}
