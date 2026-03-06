package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object CaseRoutes {

  type Env = AuthService & CaseService & DocumentService

  private def statusQuery(req: Request): Option[CaseStatus] = {
    req.url.queryParams.queryParam("status").flatMap { s =>
      CaseStatus.values.find(_.toString == s)
    }
  }

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "cases" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[ComplianceCaseCreate](req)
          svc <- ZIO.service[CaseService]
          out <- svc.create(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "cases" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          svc <- ZIO.service[CaseService]
          out <- svc.list(statusQuery(req), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "cases" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            svc <- ZIO.service[CaseService]
            out <- svc.get(CaseId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "cases" / string(
        "id"
      ) / "assign" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[CaseAssign](req)
          svc <- ZIO.service[CaseService]
          out <- svc.assign(CaseId(id), body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "cases" / string(
        "id"
      ) / "status" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[CaseStatusUpdate](req)
          svc <- ZIO.service[CaseService]
          out <- svc.setStatus(CaseId(id), body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.POST / "api" / "v1" / "cases" / string("id") / "tasks" -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            body <- JsonSupport.decode[CaseTaskCreate](req)
            svc <- ZIO.service[CaseService]
            out <- svc.addTask(CaseId(id), body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "cases" / string("id") / "notes" -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            body <- JsonSupport.decode[CaseNoteCreate](req)
            svc <- ZIO.service[CaseService]
            out <- svc.addNote(CaseId(id), body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "cases" / string(
        "id"
      ) / "documents" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[DocumentUpload](req)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityType must be Case"))
            .when(body.entityType != EntityType.Case)
          _ <- ZIO
            .fail(ApiError.BadRequest("entityId mismatch"))
            .when(body.entityId != id)
          docs <- ZIO.service[DocumentService]
          out <- docs.upload(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "backoffice" / "queues" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
            svc <- ZIO.service[CaseService]
            out <- svc.queues(p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      }
    )
}
