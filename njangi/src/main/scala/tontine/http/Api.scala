package tontine
package http
import zio.*
import zio.http.*
import tontine.service.*
import zio.json.EncoderOps
import zio.json.DecoderOps
object Api {
  private def jsonResponse(status: Status, body: String): Response =
    Response(
      status = status,
      headers = Headers(Header.ContentType(MediaType.application.json)),
      body = Body.fromString(body)
    )

  private def handleError(e: AppError): Response = {
    e match {
      case AppError.NotFound(msg) =>
        jsonResponse(
          Status.NotFound,
          s"""{"error":"not_found","message":${msg.toJson}}"""
        )
      case AppError.Validation(msg) =>
        jsonResponse(
          Status.BadRequest,
          s"""{"error":"validation","message":${msg.toJson}}"""
        )
      case AppError.Payment(msg) =>
        jsonResponse(
          Status.BadGateway,
          s"""{"error":"payment","message":${msg.toJson}}"""
        )
      case AppError.Bank(msg) =>
        jsonResponse(
          Status.BadGateway,
          s"""{"error":"bank","message":${msg.toJson}}"""
        )
    }
  }
  val routes: Routes[
    CircleService & MemberRepo & ContributionService & ScoreService,
    Nothing
  ] =
    Routes(
      Method.GET / "health" ->
        handler(Response.text("ok")),

      Method.POST / "members" ->
        handler { (req: Request) =>
          (for {
            body <- req.body.asString
            data <- ZIO.fromEither(
              body.fromJson[CreateMemberReq].left.map(AppError.Validation(_))
            )
            id <- MemberId.random
            m = Member(id, data.fullName, data.phoneE164)
            repo <- ZIO.service[MemberRepo]
            _ <- repo.create(m)
          } yield jsonResponse(Status.Created, m.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "circles" ->
        handler { (req: Request) =>
          (for {
            body <- req.body.asString
            data <- ZIO.fromEither(
              body.fromJson[CreateCircleReq].left.map(AppError.Validation(_))
            )
            svc <- ZIO.service[CircleService]
            c <- svc.createCircle(data.name, data.bankAccountRef)
          } yield jsonResponse(Status.Created, c.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "circles" / string("circleId") / "join" ->
        handler { (circleIdStr: String, req: Request) =>
          (for {
            circleId <- ZIO
              .attempt(java.util.UUID.fromString(circleIdStr))
              .map(
                CircleId.fromUUID(_)
              )
              .orElseFail(AppError.Validation("Invalid circleId UUID"))
            body <- req.body.asString
            data <- ZIO.fromEither(
              body.fromJson[JoinCircleReq].left.map(AppError.Validation(_))
            )
            svc <- ZIO.service[CircleService]
            c <- svc.addMember(circleId, data.memberId)
          } yield jsonResponse(Status.Ok, c.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "circles" / string("circleId") / "contributions" ->
        handler { (circleIdStr: String, req: Request) =>
          (for {
            circleId <- ZIO
              .attempt(java.util.UUID.fromString(circleIdStr))
              .map(uuid => (uuid: java.util.UUID))
              .map(_.asInstanceOf[CircleId]) // safe for opaque UUID
              .orElseFail(AppError.Validation("Invalid circleId UUID"))
            body <- req.body.asString
            data <- ZIO.fromEither(
              body
                .fromJson[StartContributionReq]
                .left
                .map(AppError.Validation(_))
            )
            svc <- ZIO.service[ContributionService]
            c <- svc.startContribution(
              circleId,
              data.memberId,
              data.amount,
              data.dueDate
            )
          } yield jsonResponse(Status.Created, c.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "contributions" / string("contributionId") / "confirm" ->
        handler { (contributionIdStr: String, _: Request) =>
          (for {
            cid <- ZIO
              .attempt(java.util.UUID.fromString(contributionIdStr))
              .map(_.asInstanceOf[ContributionId])
              .orElseFail(AppError.Validation("Invalid contributionId UUID"))
            svc <- ZIO.service[ContributionService]
            c <- svc.confirmContribution(cid)
          } yield jsonResponse(Status.Ok, c.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "circles" / string("circleId") / "sweep" ->
        handler { (circleIdStr: String, _: Request) =>
          (for {
            circleId <- ZIO
              .attempt(java.util.UUID.fromString(circleIdStr))
              .map(_.asInstanceOf[CircleId])
              .orElseFail(AppError.Validation("Invalid circleId UUID"))
            svc <- ZIO.service[ContributionService]
            n <- svc
              .sweepAndReconcile(circleId, since = java.time.Instant.EPOCH)
          } yield jsonResponse(Status.Ok, s"""{"reconciled":$n}""")).catchAll(
            e => ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.GET / "members" / string("memberId") / "score" ->
        handler { (memberIdStr: String, _: Request) =>
          (for {
            memberId <- ZIO
              .attempt(java.util.UUID.fromString(memberIdStr))
              .map(_.asInstanceOf[MemberId])
              .orElseFail(AppError.Validation("Invalid memberId UUID"))
            svc <- ZIO.service[ScoreService]
            s <- svc.compute(memberId)
          } yield jsonResponse(Status.Ok, s.toJson)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        },

      Method.POST / "members" / string("memberId") / "score" / "export" ->
        handler { (memberIdStr: String, _: Request) =>
          (for {
            memberId <- ZIO
              .attempt(java.util.UUID.fromString(memberIdStr))
              .map(_.asInstanceOf[MemberId])
              .orElseFail(AppError.Validation("Invalid memberId UUID"))
            svc <- ZIO.service[ScoreService]
            cred <- svc.exportSignedCredential(memberId)
          } yield jsonResponse(Status.Ok, cred)).catchAll(e =>
            ZIO.succeed(handleError(AppError.Validation(e.getMessage)))
          )
        }
    )
}
