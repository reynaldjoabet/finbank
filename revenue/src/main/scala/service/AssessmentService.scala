package revenue.service

import zio.*
import zio.json.ast.Json
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait AssessmentService {
  def create(
      req: AssessmentCreate,
      principal: Principal
  ): IO[ApiError, (Assessment, List[Liability])]
  def get(id: AssessmentId, principal: Principal): IO[ApiError, Assessment]
  def listLiabilities(
      taxpayerId: TaxpayerId,
      status: Option[LiabilityStatus],
      principal: Principal
  ): IO[ApiError, List[Liability]]
  def getLiability(
      id: LiabilityId,
      principal: Principal
  ): IO[ApiError, Liability]
  def recalcLiability(
      id: LiabilityId,
      principal: Principal
  ): IO[ApiError, LiabilityRecalcResult]
}

object AssessmentService {

  private def extractTaxDue(payload: Json): BigDecimal = {
    payload match {
      case Json.Obj(fields) =>
        fields
          .collectFirst { case ("taxDue", Json.Num(n)) =>
            BigDecimal(n.toString)
          }
          .getOrElse(BigDecimal(0))
      case _ => BigDecimal(0)
    }
  }

  val live: URLayer[
    ReturnRepo & AssessmentRepo & AuditService & Clock,
    AssessmentService
  ] =
    ZLayer.fromZIO {
      for {
        returns <- ZIO.service[ReturnRepo]
        repo <- ZIO.service[AssessmentRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new AssessmentService {

        override def create(
            req: AssessmentCreate,
            principal: Principal
        ): IO[ApiError, (Assessment, List[Liability])] = {
          for {
            trOpt <- returns.get(req.returnId).mapError(ApiError.fromRepo)
            tr <- ZIO
              .fromOption(trOpt)
              .orElseFail(
                ApiError.NotFound(s"Return not found: ${req.returnId.value}")
              )
            _ <- ZIO
              .fail(
                ApiError.BadRequest(
                  "Return must be submitted before assessment"
                )
              )
              .unless(tr.status == ReturnStatus.Submitted)

            now <- clock.instant.map(_.toEpochMilli)
            aid <- Random.nextUUID.map(u => AssessmentId(u.toString))
            assessedAmount = extractTaxDue(tr.payload)
            penalty =
              if (assessedAmount > 0) assessedAmount * 0.02 else BigDecimal(0)
            interest =
              if (assessedAmount > 0) assessedAmount * 0.01 else BigDecimal(0)
            a = Assessment(
              id = aid,
              returnId = tr.id,
              taxpayerId = tr.taxpayerId,
              taxType = tr.taxType,
              assessedAmount = assessedAmount,
              penalty = penalty,
              interest = interest,
              currency = "MUR",
              createdAtEpochMs = now
            )

            lid <- Random.nextUUID.map(u => LiabilityId(u.toString))
            due = now + 30L * 24 * 3600 * 1000 // +30 days MVP
            l = Liability(
              id = lid,
              taxpayerId = tr.taxpayerId,
              assessmentId = aid,
              taxType = tr.taxType,
              amount = assessedAmount + penalty + interest,
              currency = "MUR",
              dueDateEpochMs = due,
              status = LiabilityStatus.Open
            )

            _ <- repo.create(a, List(l)).mapError(ApiError.fromRepo)
            _ <- returns
              .setStatus(
                tr.id,
                ReturnStatus.Assessed,
                now,
                tr.submittedAtEpochMs
              )
              .mapError(ApiError.fromRepo)
              .unit
            _ <- audit.record(
              principal,
              "ASSESSMENT_CREATED",
              "Assessment",
              aid.value,
              s"return=${tr.id.value} amount=${l.amount}"
            )
          } yield (a, List(l))
        }

        override def get(
            id: AssessmentId,
            principal: Principal
        ): IO[ApiError, Assessment] =
          repo.get(id).mapError(ApiError.fromRepo).flatMap { opt =>
            ZIO
              .fromOption(opt)
              .orElseFail(
                ApiError.NotFound(s"Assessment not found: ${id.value}")
              )
          }

        override def listLiabilities(
            taxpayerId: TaxpayerId,
            status: Option[LiabilityStatus],
            principal: Principal
        ): IO[ApiError, List[Liability]] =
          repo.listLiabilities(taxpayerId, status).mapError(ApiError.fromRepo)

        override def getLiability(
            id: LiabilityId,
            principal: Principal
        ): IO[ApiError, Liability] =
          repo.getLiability(id).mapError(ApiError.fromRepo).flatMap { opt =>
            ZIO
              .fromOption(opt)
              .orElseFail(
                ApiError.NotFound(s"Liability not found: ${id.value}")
              )
          }

        override def recalcLiability(
            id: LiabilityId,
            principal: Principal
        ): IO[ApiError, LiabilityRecalcResult] = {
          for {
            lOpt <- repo.getLiability(id).mapError(ApiError.fromRepo)
            l <- ZIO
              .fromOption(lOpt)
              .orElseFail(
                ApiError.NotFound(s"Liability not found: ${id.value}")
              )

            // MVP recalc: add small daily interest if overdue
            now <- clock.instant.map(_.toEpochMilli)
            overdueDays = math.max(
              0L,
              (now - l.dueDateEpochMs) / (24L * 3600L * 1000L)
            )
            interestAdded = BigDecimal(overdueDays) * BigDecimal(
              0.005
            ) * l.amount
            penaltyAdded =
              if (overdueDays > 0) l.amount * 0.01 else BigDecimal(0)

            old = l.amount
            next = l.copy(amount = l.amount + interestAdded + penaltyAdded)
            _ <- repo.updateLiability(next).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "LIABILITY_RECALCULATED",
              "Liability",
              l.id.value,
              s"old=$old new=${next.amount}"
            )

          } yield LiabilityRecalcResult(
            l.id,
            old,
            next.amount,
            penaltyAdded,
            interestAdded
          )
        }
      }
    }
}
