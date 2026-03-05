package com.migrantbank.service

import com.migrantbank.db.Db
import com.migrantbank.domain.*
import com.migrantbank.repo.*
import zio.*
import java.util.UUID

trait PaycheckService {
  def enroll(
      userId: UUID,
      employerName: String,
      correlationId: String
  ): IO[AppError, PaycheckEnrollment]
  def get(userId: UUID): IO[AppError, Option[PaycheckEnrollment]]
}

object PaycheckService {
  val live: ZLayer[Db, Nothing, PaycheckService] =
    ZLayer.fromFunction { (db: Db) =>
      new PaycheckService {

        override def enroll(
            userId: UUID,
            employerName: String,
            correlationId: String
        ): IO[AppError, PaycheckEnrollment] =
          if employerName.trim.isEmpty then
            ZIO.fail(AppError.Validation("Employer name required"))
          else
            for {
              now <- Clock.instant
              id <- Random.nextUUID
              ref = id.toString
              e = PaycheckEnrollment(userId, employerName, ref, now)
              _ <- db.transaction {
                PaycheckRepo.upsert(e)
                AuditRepo.append(
                  "paycheck_enrolled",
                  Some(userId),
                  correlationId,
                  s"employer=$employerName"
                )
              }
            } yield e

        override def get(
            userId: UUID
        ): IO[AppError, Option[PaycheckEnrollment]] =
          db.query {
            PaycheckRepo.get(userId)
          }
      }
    }
}
