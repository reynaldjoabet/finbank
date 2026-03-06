package revenue.api

import zio.*
import zio.http.*
import zio.json.*
import revenue.service.ApiError

object JsonSupport {

  def decode[A: JsonDecoder](req: Request): IO[ApiError, A] = {
    req.body.asString.orElseFail(ApiError.BadRequest("Missing body")).flatMap {
      body =>
        ZIO.fromEither(body.fromJson[A].left.map(ApiError.BadRequest(_)))
    }
  }

  def okJson[A: JsonEncoder](a: A): Response =
    Response.json(a.toJson)

  def errorJson(err: ApiError): Response = {
    val (status, msg) =
      err match {
        case ApiError.BadRequest(m)   => (Status.BadRequest, m)
        case ApiError.Unauthorized(m) => (Status.Unauthorized, m)
        case ApiError.Forbidden(m)    => (Status.Forbidden, m)
        case ApiError.NotFound(m)     => (Status.NotFound, m)
        case ApiError.Conflict(m)     => (Status.Conflict, m)
        case ApiError.Internal(m)     => (Status.InternalServerError, m)
      }
    Response(
      status = status,
      body = Body.fromString(s"""{"error":${msg.toJson}}"""),
      headers = Headers(Header.ContentType(MediaType.application.json))
    )
  }
}
