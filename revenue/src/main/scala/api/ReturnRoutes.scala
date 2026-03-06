package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object ReturnRoutes {

  type Env = AuthService & ReturnService & DocumentService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "returns" / "drafts" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
            body <- JsonSupport.decode[ReturnDraftCreate](req)
            svc <- ZIO.service[ReturnService]
            out <- svc.createDraft(body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.PUT / "api" / "v1" / "returns" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
            body <- JsonSupport.decode[ReturnDraftUpdate](req)
            svc <- ZIO.service[ReturnService]
            out <- svc.updateDraft(ReturnId(id), body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "returns" / string(
        "id"
      ) / "validate" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          svc <- ZIO.service[ReturnService]
          out <- svc.validate(ReturnId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "returns" / string(
        "id"
      ) / "submit" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          svc <- ZIO.service[ReturnService]
          out <- svc.submit(ReturnId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "returns" / string(
        "id"
      ) / "amend" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          svc <- ZIO.service[ReturnService]
          out <- svc.amend(ReturnId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "returns" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            svc <- ZIO.service[ReturnService]
            out <- svc.get(ReturnId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.GET / "api" / "v1" / "taxpayers" / string(
        "tp"
      ) / "returns" -> handler { (tp: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[ReturnService]
          out <- svc.listByTaxpayer(TaxpayerId(tp), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      // Generic attachment upload for a return
      Method.POST / "api" / "v1" / "returns" / string(
        "id"
      ) / "attachments" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          body <- JsonSupport.decode[DocumentUpload](req)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityType must be Return"))
            .when(body.entityType != EntityType.Return)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityId mismatch"))
            .when(body.entityId != id)
          docs <- ZIO.service[DocumentService]
          out <- docs.upload(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
