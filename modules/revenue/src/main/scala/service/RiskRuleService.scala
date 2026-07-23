package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*
import zio.json.ast.Json

trait RiskRuleService {
  def list(principal: Principal): IO[ApiError, List[RiskRule]]
  def create(req: RiskRuleCreate, principal: Principal): IO[ApiError, RiskRule]
  def enable(
      id: RiskRuleId,
      enabled: Boolean,
      principal: Principal
  ): IO[ApiError, RiskRule]

  def evaluate(taxType: TaxType, payload: Json): IO[ApiError, List[RiskRule]]
}

object RiskRuleService {

  private def extractNumber(
      payload: Json,
      field: String
  ): Option[BigDecimal] = {
    payload match {
      case Json.Obj(fields) =>
        fields.collectFirst {
          case (k, Json.Num(n)) if k == field => BigDecimal(n.toString)
        }
      case _ => None
    }
  }

  val live: URLayer[RiskRuleRepo & AuditService & Clock, RiskRuleService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[RiskRuleRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new RiskRuleService {

        override def list(principal: Principal): IO[ApiError, List[RiskRule]] =
          repo.list().mapError(ApiError.fromRepo)

        override def create(
            req: RiskRuleCreate,
            principal: Principal
        ): IO[ApiError, RiskRule] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => RiskRuleId(u.toString))
            rule = RiskRule(
              id = id,
              name = req.name,
              enabled = true,
              taxTypes = req.taxTypes,
              jsonField = req.jsonField,
              threshold = req.threshold,
              caseType = req.caseType,
              caseReason = req.caseReason,
              createdAtEpochMs = now,
              updatedAtEpochMs = now
            )
            saved <- repo.create(rule, now).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "RISK_RULE_CREATED",
              "RiskRule",
              saved.id.value,
              saved.name
            )
          } yield saved
        }

        override def enable(
            id: RiskRuleId,
            enabled: Boolean,
            principal: Principal
        ): IO[ApiError, RiskRule] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            saved <- repo
              .setEnabled(id, enabled, now)
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              if (enabled) "RISK_RULE_ENABLED" else "RISK_RULE_DISABLED",
              "RiskRule",
              saved.id.value,
              saved.name
            )
          } yield saved
        }

        override def evaluate(
            taxType: TaxType,
            payload: Json
        ): IO[ApiError, List[RiskRule]] = {
          for {
            all <- repo.list().mapError(ApiError.fromRepo)
            hits = all.filter { r =>
              r.enabled &&
              r.taxTypes.forall(_.contains(taxType)) &&
              extractNumber(payload, r.jsonField).exists(_ >= r.threshold)
            }
          } yield hits
        }
      }
    }
}
