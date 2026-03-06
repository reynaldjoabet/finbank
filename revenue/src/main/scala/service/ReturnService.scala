package revenue.service

import zio.*
import zio.json.ast.Json
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait ReturnService {
  def createDraft(
      req: ReturnDraftCreate,
      principal: Principal
  ): IO[ApiError, TaxReturn]
  def updateDraft(
      id: ReturnId,
      req: ReturnDraftUpdate,
      principal: Principal
  ): IO[ApiError, TaxReturn]
  def validate(
      id: ReturnId,
      principal: Principal
  ): IO[ApiError, ValidationResult]
  def submit(id: ReturnId, principal: Principal): IO[ApiError, TaxReturn]
  def amend(id: ReturnId, principal: Principal): IO[ApiError, TaxReturn]
  def get(id: ReturnId, principal: Principal): IO[ApiError, TaxReturn]
  def listByTaxpayer(
      taxpayerId: TaxpayerId,
      principal: Principal
  ): IO[ApiError, List[TaxReturn]]
}

object ReturnService {

  private def basicValidate(
      taxType: TaxType,
      payload: Json
  ): ValidationResult = {
    // Minimal examples:
    // - VAT: expects "turnover" numeric
    // - IncomeTax: expects "taxDue" numeric
    // - Payroll: expects "employees" numeric
    val required = taxType match {
      case TaxType.VAT                  => List("turnover")
      case TaxType.IncomeTax            => List("taxDue")
      case TaxType.Payroll_PAYE_CSG_NSF => List("employees")
      case _                            => Nil
    }

    val fields: Set[String] =
      payload match {
        case Json.Obj(fs) => fs.map(_._1).toSet
        case _            => Set.empty
      }

    val missing = required.filterNot(fields.contains)
    if (missing.isEmpty) { ValidationResult(ok = true, errors = Nil) }
    else {
      ValidationResult(
        ok = false,
        errors = missing.map(f => s"Missing required field: $f")
      )
    }
  }

  val live: URLayer[
    ReturnRepo & TaxpayerRepo & RiskRuleService & CaseRepo & AuditService &
      Clock,
    ReturnService
  ] =
    ZLayer.fromZIO {
      for {
        returns <- ZIO.service[ReturnRepo]
        taxpayers <- ZIO.service[TaxpayerRepo]
        rules <- ZIO.service[RiskRuleService]
        cases <- ZIO.service[CaseRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new ReturnService {

        override def createDraft(
            req: ReturnDraftCreate,
            principal: Principal
        ): IO[ApiError, TaxReturn] = {
          for {
            tpOpt <- taxpayers.get(req.taxpayerId).mapError(ApiError.fromRepo)
            _ <- ZIO
              .fail(
                ApiError.NotFound(s"Unknown taxpayer: ${req.taxpayerId.value}")
              )
              .when(tpOpt.isEmpty)

            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => ReturnId(u.toString))
            tr <- returns.createDraft(req, now, id).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "RETURN_DRAFT_CREATED",
              "Return",
              tr.id.value,
              s"${tr.taxType} ${tr.period}"
            )
          } yield tr
        }

        override def updateDraft(
            id: ReturnId,
            req: ReturnDraftUpdate,
            principal: Principal
        ): IO[ApiError, TaxReturn] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            tr <- returns
              .updateDraft(id, req.payload, now)
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "RETURN_DRAFT_UPDATED",
              "Return",
              tr.id.value,
              s"version=${tr.version}"
            )
          } yield tr
        }

        override def validate(
            id: ReturnId,
            principal: Principal
        ): IO[ApiError, ValidationResult] = {
          for {
            trOpt <- returns.get(id).mapError(ApiError.fromRepo)
            tr <- ZIO
              .fromOption(trOpt)
              .orElseFail(ApiError.NotFound(s"Return not found: ${id.value}"))
            res = basicValidate(tr.taxType, tr.payload)
            now <- clock.instant.map(_.toEpochMilli)
            _ <- ZIO.when(res.ok) {
              returns
                .setStatus(id, ReturnStatus.Validated, now, None)
                .mapError(ApiError.fromRepo)
                .unit
            }
            _ <- audit.record(
              principal,
              "RETURN_VALIDATED",
              "Return",
              tr.id.value,
              s"ok=${res.ok} errors=${res.errors.size}"
            )
          } yield res
        }

        override def submit(
            id: ReturnId,
            principal: Principal
        ): IO[ApiError, TaxReturn] = {
          for {
            trOpt <- returns.get(id).mapError(ApiError.fromRepo)
            tr <- ZIO
              .fromOption(trOpt)
              .orElseFail(ApiError.NotFound(s"Return not found: ${id.value}"))

            // Require validation (or enforce in validate step)
            _ <- ZIO
              .fail(
                ApiError.BadRequest(
                  "Return must be validated before submission"
                )
              )
              .when(
                tr.status != ReturnStatus.Validated && tr.status != ReturnStatus.Draft
              )

            res = basicValidate(tr.taxType, tr.payload)
            _ <- ZIO
              .fail(
                ApiError.BadRequest(
                  s"Validation failed: ${res.errors.mkString(", ")}"
                )
              )
              .unless(res.ok)

            now <- clock.instant.map(_.toEpochMilli)
            submitted <- returns
              .setStatus(id, ReturnStatus.Submitted, now, Some(now))
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "RETURN_SUBMITTED",
              "Return",
              submitted.id.value,
              s"version=${submitted.version}"
            )

            // Evaluate risk rules; open case(s) if needed
            hits <- rules.evaluate(submitted.taxType, submitted.payload)
            _ <- ZIO.foreachDiscard(hits) { rule =>
              for {
                cid <- Random.nextUUID.map(u => CaseId(u.toString))
                c = ComplianceCase(
                  id = cid,
                  caseType = rule.caseType,
                  taxpayerId = submitted.taxpayerId,
                  reason =
                    s"${rule.caseReason} (rule=${rule.name}, return=${submitted.id.value})",
                  status = CaseStatus.Open,
                  assignedTo = None,
                  createdAtEpochMs = now,
                  updatedAtEpochMs = now
                )
                _ <- cases.create(c).mapError(ApiError.fromRepo)
                _ <- audit.record(
                  principal,
                  "CASE_OPENED",
                  "Case",
                  cid.value,
                  s"auto from return ${submitted.id.value}"
                )
              } yield ()
            }
          } yield submitted
        }

        override def amend(
            id: ReturnId,
            principal: Principal
        ): IO[ApiError, TaxReturn] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            newId <- Random.nextUUID.map(u => ReturnId(u.toString))
            tr <- returns.amend(id, now, newId).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "RETURN_AMENDED",
              "Return",
              tr.id.value,
              s"amendedFrom=${tr.amendedFrom.map(_.value).getOrElse("-")}"
            )
          } yield tr
        }

        override def get(
            id: ReturnId,
            principal: Principal
        ): IO[ApiError, TaxReturn] = {
          for {
            trOpt <- returns.get(id).mapError(ApiError.fromRepo)
            tr <- ZIO
              .fromOption(trOpt)
              .orElseFail(ApiError.NotFound(s"Return not found: ${id.value}"))
          } yield tr
        }

        override def listByTaxpayer(
            taxpayerId: TaxpayerId,
            principal: Principal
        ): IO[ApiError, List[TaxReturn]] =
          returns.listByTaxpayer(taxpayerId).mapError(ApiError.fromRepo)
      }
    }
}
