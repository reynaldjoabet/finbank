package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object ObjectionRoutes {

  type Env = AuthService & ObjectionService & DocumentService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "objections" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          body <- JsonSupport.decode[ObjectionCreate](req)
          svc <- ZIO.service[ObjectionService]
          out <- svc.create(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "objections" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            svc <- ZIO.service[ObjectionService]
            out <- svc.get(ObjectionId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "objections" / string(
        "id"
      ) / "documents" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          body <- JsonSupport.decode[DocumentUpload](req)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityType must be Objection"))
            .when(body.entityType != EntityType.Objection)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityId mismatch"))
            .when(body.entityId != id)
          docs <- ZIO.service[DocumentService]
          out <- docs.upload(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "objections" / string(
        "id"
      ) / "withdraw" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          svc <- ZIO.service[ObjectionService]
          out <- svc.withdraw(ObjectionId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
