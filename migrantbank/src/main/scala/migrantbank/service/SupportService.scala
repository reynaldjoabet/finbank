package com.migrantbank.service

import com.migrantbank.db.Db
import com.migrantbank.domain.*
import com.migrantbank.repo.*
import zio.*
import java.util.UUID

trait SupportService {
  def create(
      userId: UUID,
      message: String,
      correlationId: String
  ): IO[AppError, SupportTicket]
  def list(userId: UUID): IO[AppError, List[SupportTicket]]
}

object SupportService {
  val live: ZLayer[Db, Nothing, SupportService] =
    ZLayer.fromFunction { (db: Db) =>
      new SupportService {

        override def create(
            userId: UUID,
            message: String,
            correlationId: String
        ): IO[AppError, SupportTicket] =
          if message.trim.isEmpty then
            ZIO.fail(AppError.Validation("Message required"))
          else
            for {
              id <- Random.nextUUID
              now <- Clock.instant
              t = SupportTicket(id, userId, message, TicketStatus.OPEN, now)
              _ <- db.transaction {
                TicketRepo.insert(t)
                AuditRepo.append(
                  "support_ticket_created",
                  Some(userId),
                  correlationId,
                  s"ticketId=$id"
                )
              }
            } yield t

        override def list(userId: UUID): IO[AppError, List[SupportTicket]] =
          db.query {
            TicketRepo.listByUser(userId)
          }
      }
    }
}
