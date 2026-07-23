package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait CaseService {
  def create(
      req: ComplianceCaseCreate,
      principal: Principal
  ): IO[ApiError, ComplianceCase]
  def get(id: CaseId, principal: Principal): IO[ApiError, ComplianceCase]
  def list(
      status: Option[CaseStatus],
      principal: Principal
  ): IO[ApiError, List[ComplianceCase]]
  def assign(
      id: CaseId,
      req: CaseAssign,
      principal: Principal
  ): IO[ApiError, ComplianceCase]
  def setStatus(
      id: CaseId,
      req: CaseStatusUpdate,
      principal: Principal
  ): IO[ApiError, ComplianceCase]
  def addTask(
      id: CaseId,
      req: CaseTaskCreate,
      principal: Principal
  ): IO[ApiError, CaseTask]
  def addNote(
      id: CaseId,
      req: CaseNoteCreate,
      principal: Principal
  ): IO[ApiError, CaseNote]
  def queues(principal: Principal): IO[ApiError, QueueSummary]
}

object CaseService {

  val live: URLayer[CaseRepo & AuditService & Clock, CaseService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[CaseRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new CaseService {

        override def create(
            req: ComplianceCaseCreate,
            principal: Principal
        ): IO[ApiError, ComplianceCase] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => CaseId(u.toString))
            c = ComplianceCase(
              id,
              req.caseType,
              req.taxpayerId,
              req.reason,
              CaseStatus.Open,
              None,
              now,
              now
            )
            saved <- repo.create(c).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "CASE_CREATED",
              "Case",
              saved.id.value,
              saved.reason
            )
          } yield saved
        }

        override def get(
            id: CaseId,
            principal: Principal
        ): IO[ApiError, ComplianceCase] =
          repo
            .get(id)
            .mapError(ApiError.fromRepo)
            .flatMap(opt =>
              ZIO
                .fromOption(opt)
                .orElseFail(ApiError.NotFound(s"Case not found: ${id.value}"))
            )

        override def list(
            status: Option[CaseStatus],
            principal: Principal
        ): IO[ApiError, List[ComplianceCase]] =
          repo.list(status).mapError(ApiError.fromRepo)

        override def assign(
            id: CaseId,
            req: CaseAssign,
            principal: Principal
        ): IO[ApiError, ComplianceCase] = {
          for {
            prev <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            next <- repo
              .update(
                prev.copy(
                  status = CaseStatus.Assigned,
                  assignedTo = Some(req.assignedTo),
                  updatedAtEpochMs = now
                )
              )
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "CASE_ASSIGNED",
              "Case",
              next.id.value,
              s"to=${req.assignedTo}"
            )
          } yield next
        }

        override def setStatus(
            id: CaseId,
            req: CaseStatusUpdate,
            principal: Principal
        ): IO[ApiError, ComplianceCase] = {
          for {
            prev <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            next <- repo
              .update(prev.copy(status = req.status, updatedAtEpochMs = now))
              .mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "CASE_STATUS_UPDATED",
              "Case",
              next.id.value,
              req.status.toString
            )
          } yield next
        }

        override def addTask(
            id: CaseId,
            req: CaseTaskCreate,
            principal: Principal
        ): IO[ApiError, CaseTask] = {
          for {
            _ <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            tid <- Random.nextUUID.map(_.toString)
            t = CaseTask(
              tid,
              id,
              req.title,
              req.dueEpochMs,
              done = false,
              createdAtEpochMs = now
            )
            saved <- repo.addTask(t).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "CASE_TASK_ADDED",
              "Case",
              id.value,
              req.title
            )
          } yield saved
        }

        override def addNote(
            id: CaseId,
            req: CaseNoteCreate,
            principal: Principal
        ): IO[ApiError, CaseNote] = {
          for {
            _ <- get(id, principal)
            now <- clock.instant.map(_.toEpochMilli)
            nid <- Random.nextUUID.map(_.toString)
            n = CaseNote(nid, id, req.note, now, author = principal.subject)
            saved <- repo.addNote(n).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "CASE_NOTE_ADDED",
              "Case",
              id.value,
              "note"
            )
          } yield saved
        }

        override def queues(
            principal: Principal
        ): IO[ApiError, QueueSummary] = {
          for {
            all <- repo.list(None).mapError(ApiError.fromRepo)
            open = all.count(_.status == CaseStatus.Open)
            assigned = all.count(_.status == CaseStatus.Assigned)
            inProg = all.count(_.status == CaseStatus.InProgress)
          } yield QueueSummary(open, assigned, inProg)
        }
      }
    }
}
