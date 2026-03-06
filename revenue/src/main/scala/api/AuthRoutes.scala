package revenue.api

import zio.*
import zio.http.*
import revenue.domain.*
import revenue.service.*

object AuthRoutes {

  type Env = AuthService

  val routes: Routes[Env, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "auth" / "login" -> handler {
        (req: Request) =>
          (for {
            body <- JsonSupport.decode[LoginRequest](req)
            svc <- ZIO.service[AuthService]
            out <- svc.login(body)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "auth" / "refresh" -> handler {
        (req: Request) =>
          (for {
            body <- JsonSupport.decode[RefreshRequest](req)
            svc <- ZIO.service[AuthService]
            out <- svc.refresh(body)
          } yield JsonSupport.okJson(out)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "auth" / "logout" -> handler {
        (req: Request) =>
          (for {
            body <- JsonSupport.decode[LogoutRequest](req)
            svc <- ZIO.service[AuthService]
            _ <- svc.logout(body)
          } yield Response.status(Status.NoContent)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "auth" / "password" / "forgot" -> handler {
        (req: Request) =>
          (for {
            body <- JsonSupport.decode[ForgotPasswordRequest](req)
            svc <- ZIO.service[AuthService]
            _ <- svc.forgotPassword(body)
          } yield Response.status(Status.Accepted)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.POST / "api" / "v1" / "auth" / "password" / "reset" -> handler {
        (req: Request) =>
          (for {
            body <- JsonSupport.decode[ResetPasswordRequest](req)
            svc <- ZIO.service[AuthService]
            _ <- svc.resetPassword(body)
          } yield Response.status(Status.NoContent)).catchAll(e =>
            ZIO.succeed(JsonSupport.errorJson(e))
          )
      },

      Method.GET / "api" / "v1" / "auth" / "me" -> handler { (req: Request) =>
        (for {
          p <- HttpAuth.principal(req)
        } yield JsonSupport.okJson(p)).catchAll(e =>
          ZIO.succeed(JsonSupport.errorJson(e))
        )
      }
    )
}
