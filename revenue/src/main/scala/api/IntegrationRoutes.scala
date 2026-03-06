package revenue.api

import zio.*
import zio.http.*
import revenue.service.*
import revenue.domain.*

object IntegrationRoutes {

  type Env = AuthService & IntegrationService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "integrations" / "payments" / "webhook" -> handler {
        (req: Request) =>
          (for {
            // For production, lock this down with HMAC signatures / mTLS / allowlist.
            p <- HttpAuth.principal(req)
            _ <- HttpAuth.requireAny(p, Set(Role.Admin, Role.Officer))
            body <- JsonSupport.decode[PaymentWebhook](req)
            svc <- ZIO.service[IntegrationService]
            out <- svc.paymentWebhook(body, p)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      }
    )
}
