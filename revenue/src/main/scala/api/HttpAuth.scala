package revenue.api
import zio.*
import zio.http.*
import revenue.domain.*
import revenue.service.*

object HttpAuth {

  type Env = AuthService

  def bearer(req: Request): IO[ApiError, String] = {
    val tokenOpt =
      req.header(Header.Authorization).collect {
        case Header.Authorization.Bearer(token) => token.value.mkString
      }

    ZIO
      .fromOption(tokenOpt)
      .orElseFail(ApiError.Unauthorized("Missing Bearer token"))
  }

  def principal(req: Request): ZIO[AuthService, ApiError, Principal] = {
    for {
      token <- bearer(req)
      auth <- ZIO.service[AuthService]
      p <- auth.authenticateAccessToken(token)
    } yield p
  }

  def requireAny(p: Principal, roles: Set[Role]): IO[ApiError, Unit] = {
    val ok = roles.exists(p.roles.contains)
    ZIO
      .fail(ApiError.Forbidden(s"Missing any role of: ${roles.mkString(",")}"))
      .unless(ok)
      .unit
  }
}
