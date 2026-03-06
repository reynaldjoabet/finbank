package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object AssessmentRoutes {

  type Env = AuthService & AssessmentService

  private def statusParam(req: Request): Option[LiabilityStatus] = {
    req.url.queryParams.queryParam("status").flatMap { s =>
      LiabilityStatus.values.find(_.toString == s)
    }
  }

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "assessments" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[AssessmentCreate](req)
          svc <- ZIO.service[AssessmentService]
          out <- svc.create(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "assessments" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            svc <- ZIO.service[AssessmentService]
            out <- svc.get(AssessmentId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.GET / "api" / "v1" / "taxpayers" / string(
        "tp"
      ) / "liabilities" -> handler { (tp: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[AssessmentService]
          out <- svc.listLiabilities(TaxpayerId(tp), statusParam(req), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "liabilities" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            svc <- ZIO.service[AssessmentService]
            out <- svc.getLiability(LiabilityId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "liabilities" / string(
        "id"
      ) / "recalculate" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          svc <- ZIO.service[AssessmentService]
          out <- svc.recalcLiability(LiabilityId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
