package revenue.api
import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object TaxpayerRoutes {

  type Env = AuthService & TaxpayerService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "taxpayers" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          body <- JsonSupport.decode[TaxpayerRegistration](req)
          svc <- ZIO.service[TaxpayerService]
          out <- svc.register(body, p)
        } yield JsonSupport.okJson(out)).catchAll(e => ZIO.succeed(JsonSupport.errorJson(e)))
      },

      Method.GET / "api" / "v1" / "taxpayers" / string("id") -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[TaxpayerService]
          out <- svc.get(TaxpayerId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e => ZIO.succeed(JsonSupport.errorJson(e)))
      },

      Method.GET / "api" / "v1" / "taxpayers" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Officer, Role.Admin))
          svc <- ZIO.service[TaxpayerService]
          out <- svc.list(p)
        } yield JsonSupport.okJson(out)).catchAll(e => ZIO.succeed(JsonSupport.errorJson(e)))
      }
    )
}