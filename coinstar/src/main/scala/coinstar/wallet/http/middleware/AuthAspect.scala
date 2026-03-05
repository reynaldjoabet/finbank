package coinstar.wallet.http.middleware

import coinstar.wallet.domain.Principal
import coinstar.wallet.service.AuthService
import zio.*
import zio.http.*

object AuthAspect {

  /** Extracts Authorization: Bearer <token>, verifies it, and injects Principal
    * into ZIO environment.
    *
    * Inspired by the official zio-http authentication example.
    */
  val bearer: HandlerAspect[AuthService, Principal] =
    HandlerAspect.interceptIncomingHandler(
      Handler.fromFunctionZIO[Request] { request =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            AuthService
              .verify(token.value.asString)
              .map(principal => (request, principal))
              .mapError(_ => Response.unauthorized)
          case _ =>
            ZIO.fail(
              Response.unauthorized.addHeaders(
                Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))
              )
            )
        }
      }
    )
}
