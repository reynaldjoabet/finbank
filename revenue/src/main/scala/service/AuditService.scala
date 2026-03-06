package revenue.service

import zio.*
import revenue.domain.*
import revenue.repo.AuditRepo

trait AuditService {
  def record(
      principal: Principal,
      action: String,
      entityType: String,
      entityId: String,
      details: String
  ): UIO[Unit]
}

object AuditService {
  val live: URLayer[AuditRepo & Clock, AuditService] =
    ZLayer.fromFunction { (repo: AuditRepo, clock: Clock) =>
      new AuditService {
        override def record(
            principal: Principal,
            action: String,
            entityType: String,
            entityId: String,
            details: String
        ): UIO[Unit] = {
          clock.instant.map(_.toEpochMilli).flatMap { now =>
            repo.append(
              AuditEvent(now, principal, action, entityType, entityId, details)
            )
          }
        }
      }
    }
}
