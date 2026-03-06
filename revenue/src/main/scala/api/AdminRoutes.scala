package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.AuditRepo
import revenue.service.*

object AdminRoutes {

  type Env = AuthService & RiskRuleService & AuditRepo

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.GET / "api" / "v1" / "admin" / "risk-rules" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Admin))
            svc <- ZIO.service[RiskRuleService]
            out <- svc.list(p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "admin" / "risk-rules" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Admin))
            body <- JsonSupport.decode[RiskRuleCreate](req)
            svc <- ZIO.service[RiskRuleService]
            out <- svc.create(body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "admin" / "risk-rules" / string(
        "id"
      ) / "enable" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Admin))
          svc <- ZIO.service[RiskRuleService]
          out <- svc.enable(RiskRuleId(id), enabled = true, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "admin" / "risk-rules" / string(
        "id"
      ) / "disable" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Admin))
          svc <- ZIO.service[RiskRuleService]
          out <- svc.enable(RiskRuleId(id), enabled = false, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "backoffice" / "audit" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            repo <- ZIO.service[AuditRepo]
            out <- repo.latest(200)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      }
    )
}
