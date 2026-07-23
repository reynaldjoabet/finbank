package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait ObjectionService {
  def create(
      req: ObjectionCreate,
      principal: Principal
  ): IO[ApiError, Objection]
  def get(id: ObjectionId, principal: Principal): IO[ApiError, Objection]
  def withdraw(id: ObjectionId, principal: Principal): IO[ApiError, Objection]
}

object ObjectionService {

  val live: URLayer[ObjectionRepo & AuditService & Clock, ObjectionService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[ObjectionRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new ObjectionService {

        override def create(
            req: ObjectionCreate,
            principal: Principal
        ): IO[ApiError, Objection] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => ObjectionId(u.toString))
            o = Objection(
              id,
              req.taxpayerId,
              req.referenceType,
              req.referenceId,
              req.grounds,
              ObjectionStatus.Submitted,
              now,
              now
            )
            saved <- repo.create(o).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "OBJECTION_SUBMITTED",
              "Objection",
              saved.id.value,
              s"${saved.referenceType}:${saved.referenceId}"
            )
          } yield saved
        }

        override def get(
            id: ObjectionId,
            principal: Principal
        ): IO[ApiError, Objection] =
          repo
            .get(id)
            .mapError(ApiError.fromRepo)
            .flatMap(opt =>
              ZIO
                .fromOption(opt)
                .orElseFail(
                  ApiError.NotFound(s"Objection not found: ${id.value}")
                )
            )

        override def withdraw(
            id: ObjectionId,
            principal: Principal
        ): IO[ApiError, Objection] = {
          for {
            prev <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            next <- repo
              .update(
                prev.copy(
                  status = ObjectionStatus.Withdrawn,
                  updatedAtEpochMs = now
                )
              )
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "OBJECTION_WITHDRAWN",
              "Objection",
              next.id.value,
              "withdrawn"
            )
          } yield next
        }
      }
    }
}
