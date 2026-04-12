package migrantbank.consent

import migrantbank.api.HttpUtils.*
import migrantbank.domain.AppError
import zio.*
import zio.http.*
import zio.json.*

import java.util.UUID

/** HTTP routes for the Open Banking Consent API.
  *
  * Endpoints: POST /v1/consents — TPP creates a consent grant POST
  * /v1/consents/{id}/authorise — User authorises (bank-side redirect) DELETE
  * /v1/consents/{id} — User revokes a consent GET /v1/consents — List consents
  * for the authenticated user
  */
object ConsentRoutes {

  type Env = ConsentService

  private def mapConsentError(e: AppError): Response = mapError(e)

  val routes: Routes[Env, Nothing] =
    Routes(
      // ── POST /v1/consents ────────────────────────────────────────────────
      Method.POST / "v1" / "consents" ->
        handler { (req: Request) =>
          (for {
            dto <- parseJson[CreateConsentRequest](req)
            result <- ZIO
              .serviceWithZIO[ConsentService](_.create(dto))
              .mapError(mapConsentError)
          } yield jsonResponse(result, Status.Created)).merge
        },

      // ── POST /v1/consents/{id}/authorise ────────────────────────────────
      Method.POST / "v1" / "consents" / zio.http.codec.PathCodec
        .uuid("consentId") / "authorise" ->
        handler { (consentId: UUID, req: Request) =>
          (for {
            userIdStr <- ZIO
              .fromOption(req.headers.get("X-User-Id"))
              .orElseFail(Response.unauthorized("X-User-Id header required"))
            userId <- ZIO
              .attempt(UUID.fromString(userIdStr))
              .orElseFail(Response.badRequest("Invalid X-User-Id"))
            consent <- ZIO
              .serviceWithZIO[ConsentService](
                _.authorise(ConsentId(consentId), userId)
              )
              .mapError(mapConsentError)
          } yield jsonResponse(consent)).merge
        },

      // ── DELETE /v1/consents/{id} ─────────────────────────────────────────
      Method.DELETE / "v1" / "consents" / zio.http.codec.PathCodec
        .uuid("consentId") ->
        handler { (consentId: UUID, req: Request) =>
          (for {
            userIdStr <- ZIO
              .fromOption(req.headers.get("X-User-Id"))
              .orElseFail(Response.unauthorized("X-User-Id header required"))
            userId <- ZIO
              .attempt(UUID.fromString(userIdStr))
              .orElseFail(Response.badRequest("Invalid X-User-Id"))
            _ <- ZIO
              .serviceWithZIO[ConsentService](
                _.revoke(ConsentId(consentId), userId)
              )
              .mapError(mapConsentError)
          } yield Response.status(Status.NoContent)).merge
        },

      // ── GET /v1/consents ─────────────────────────────────────────────────
      Method.GET / "v1" / "consents" ->
        handler { (req: Request) =>
          (for {
            userIdStr <- ZIO
              .fromOption(req.headers.get("X-User-Id"))
              .orElseFail(Response.unauthorized("X-User-Id header required"))
            userId <- ZIO
              .attempt(UUID.fromString(userIdStr))
              .orElseFail(Response.badRequest("Invalid X-User-Id"))
            list <- ZIO
              .serviceWithZIO[ConsentService](_.listByUser(userId))
              .mapError(mapConsentError)
          } yield jsonResponse(list)).merge
        }
    )
}
