package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object RefundRoutes {

  type Env = AuthService & RefundService & DocumentService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "refunds" / "claims" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
            body <- JsonSupport.decode[RefundClaimCreate](req)
            svc <- ZIO.service[RefundService]
            out <- svc.create(body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.GET / "api" / "v1" / "refunds" / "claims" / string(
        "id"
      ) -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[RefundService]
          out <- svc.get(RefundId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "taxpayers" / string(
        "tp"
      ) / "refunds" -> handler { (tp: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[RefundService]
          out <- svc.listByTaxpayer(TaxpayerId(tp), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "refunds" / "claims" / string(
        "id"
      ) / "documents" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          body <- JsonSupport.decode[DocumentUpload](req)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityType must be Refund"))
            .when(body.entityType != EntityType.Refund)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityId mismatch"))
            .when(body.entityId != id)
          docs <- ZIO.service[DocumentService]
          out <- docs.upload(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "refunds" / "claims" / string(
        "id"
      ) / "approve" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[RefundDecision](req)
          svc <- ZIO.service[RefundService]
          out <- svc.approve(RefundId(id), body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "refunds" / "claims" / string(
        "id"
      ) / "reject" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[RefundDecision](req)
          svc <- ZIO.service[RefundService]
          out <- svc.reject(RefundId(id), body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
