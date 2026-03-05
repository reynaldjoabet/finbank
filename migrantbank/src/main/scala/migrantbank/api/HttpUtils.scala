package com.migrantbank.api

import com.migrantbank.domain.*
import zio.*
import zio.http.*
import zio.json.*

object HttpUtils {

  final case class ErrorResponse(error: String) derives JsonEncoder, JsonDecoder

  def correlationId(req: Request): String =
    req.headers
      .get("X-Correlation-Id")
      .getOrElse(java.util.UUID.randomUUID().toString)

  def idempotencyKey(req: Request): Option[String] =
    req.headers
      .get("Idempotency-Key")
      .filter(_.nonEmpty)

  def bearerToken(req: Request): Option[String] =
    req.header(Header.Authorization).flatMap { h =>
      val v = h.renderedValue
      if v.toLowerCase.startsWith("bearer ") then Some(v.drop(7).trim) else None
    }

  def jsonResponse[A: JsonEncoder](a: A, status: Status = Status.Ok): Response =
    Response.json(a.toJson).status(status)

  def error(status: Status, msg: String): Response =
    Response.json(ErrorResponse(msg).toJson).status(status)

  def mapError(err: AppError): Response =
    err match {
      case AppError.Validation(m)          => error(Status.BadRequest, m)
      case AppError.Unauthorized(m)        => error(Status.Unauthorized, m)
      case AppError.Forbidden(m)           => error(Status.Forbidden, m)
      case AppError.NotFound(m)            => error(Status.NotFound, m)
      case AppError.Conflict(m)            => error(Status.Conflict, m)
      case AppError.ProviderUnavailable(m) =>
        error(Status.ServiceUnavailable, m)
      case AppError.RateLimited(m) => error(Status.TooManyRequests, m)
      case AppError.Internal(m, _) => error(Status.InternalServerError, m)
    }

  def parseJson[A: JsonDecoder](req: Request): IO[Response, A] =
    req.body.asString.orDie.flatMap { s =>
      ZIO.fromEither(
        s.fromJson[A]
          .left
          .map(e => error(Status.BadRequest, s"Invalid JSON: $e"))
      )
    }
}
