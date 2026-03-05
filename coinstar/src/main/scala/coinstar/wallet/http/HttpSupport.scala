package coinstar.wallet.http

import coinstar.wallet.http.dto.ErrorDto
import zio.*
import zio.http.*
import zio.json.*

object HttpSupport {

  def jsonResponse[A: JsonEncoder](a: A, status: Status = Status.Ok): Response =
    Response.json(a.toJson).status(status)

  def errorResponse(status: Status, code: String, message: String): Response =
    jsonResponse(ErrorDto(code, message), status)

  def decodeJson[A: JsonDecoder](req: Request): IO[Response, A] =
    req.body.asString
      .mapError(_ =>
        errorResponse(
          Status.BadRequest,
          "bad_request",
          "Missing/invalid request body"
        )
      )
      .flatMap { body =>
        ZIO.fromEither(
          body
            .fromJson[A]
            .left
            .map(err => errorResponse(Status.BadRequest, "bad_request", err))
        )
      }
}
