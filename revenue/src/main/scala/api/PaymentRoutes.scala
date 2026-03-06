package revenue.api
import zio.*
import zio.http.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.service.*

object PaymentRoutes {

  type Env = AuthService & PaymentService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "payments" / "intents" -> handler {
        (req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
            body <- JsonSupport.decode[PaymentIntentCreate](req)
            svc <- ZIO.service[PaymentService]
            out <- svc.createIntent(body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.GET / "api" / "v1" / "payments" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            p <- HttpAuth.principal(req)
            svc <- ZIO.service[PaymentService]
            out <- svc.get(PaymentId(id), p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "payments" / string(
        "id"
      ) / "confirm" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          _ <- HttpAuth.requireAny(p, Set(Role.Taxpayer, Role.Agent))
          body <- JsonSupport.decode[PaymentConfirm](req)
          svc <- ZIO.service[PaymentService]
          out <- svc.confirm(PaymentId(id), body, p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "payments" / string(
        "id"
      ) / "receipt" -> handler { (id: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[PaymentService]
          out <- svc.receipt(PaymentId(id), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      },

      Method.GET / "api" / "v1" / "taxpayers" / string(
        "tp"
      ) / "payments" -> handler { (tp: String, req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
          svc <- ZIO.service[PaymentService]
          out <- svc.listByTaxpayer(TaxpayerId(tp), p)
        } yield JsonSupport.okJson(out)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
