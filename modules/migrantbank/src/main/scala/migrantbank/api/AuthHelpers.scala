package migrantbank.api

import HttpUtils.*
import migrantbank.domain.*
import migrantbank.security.JwtService
import zio.*
import zio.http.*

object AuthHelpers {

  def requireAuth(req: Request): ZIO[JwtService, Response, AuthContext] =
    bearerToken(req) match {
      case None =>
        ZIO.fail(
          error(Status.Unauthorized, "Missing Authorization: Bearer token")
        )
      case Some(t) =>
        ZIO.serviceWithZIO[JwtService](_.verifyAccess(t)).mapError(mapError)
    }

  def requireAdmin(req: Request): ZIO[JwtService, Response, AuthContext] =
    requireAuth(req).flatMap { ctx =>
      if ctx.role == "admin" then ZIO.succeed(ctx)
      else ZIO.fail(error(Status.Forbidden, "Admin role required"))
    }

}
