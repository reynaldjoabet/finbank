package migrantbank.service

import com.augustnagro.magnum.*
import migrantbank.db.Db
import migrantbank.domain.*
import migrantbank.repo.*
import zio.*
import zio.json.*
import java.util.UUID

trait AdminService {
  def pendingKyc(): IO[AppError, List[User]]
  def setKyc(
      userId: UUID,
      status: KycStatus,
      correlationId: String
  ): IO[AppError, Unit]
  def updateCardDelivery(
      cardId: UUID,
      status: DeliveryStatus,
      correlationId: String
  ): IO[AppError, Unit]
  def analytics(): IO[AppError, AdminService.Analytics]
  def audit(limit: Int): IO[AppError, List[AuditEvent]]
  def flaggedTransfers(limit: Int): IO[AppError, List[Transfer]]
}

object AdminService {

  final case class Analytics(
      totalUsers: Long,
      totalTransfers: Long,
      totalTransferVolumeMinor: Long,
      riskFlaggedTransfers: Long,
      openTickets: Long
  ) derives JsonEncoder,
        JsonDecoder

  val live: ZLayer[Db, Nothing, AdminService] =
    ZLayer.fromFunction { (db: Db) =>
      new AdminService {

        override def pendingKyc(): IO[AppError, List[User]] =
          db.query {
            UserRepo.listPendingKyc()
          }.map(_.map { u =>
            User(
              id = u.id,
              profile = UserProfile(
                u.firstName,
                u.lastName,
                u.dateOfBirth,
                u.phone,
                u.address,
                "***"
              ),
              kycStatus = u.kycStatus,
              role = u.role,
              createdAt = u.createdAt
            )
          })

        override def setKyc(
            userId: UUID,
            status: KycStatus,
            correlationId: String
        ): IO[AppError, Unit] =
          db.transaction {
            UserRepo.updateKyc(userId, status)
            AuditRepo.append(
              "admin_set_kyc",
              Some(userId),
              correlationId,
              s"status=$status"
            )
          }

        override def updateCardDelivery(
            cardId: UUID,
            status: DeliveryStatus,
            correlationId: String
        ): IO[AppError, Unit] =
          db.transaction {
            CardRepo.updateDelivery(cardId, status)
            AuditRepo.append(
              "admin_card_delivery",
              None,
              correlationId,
              s"cardId=$cardId status=$status"
            )
          }

        override def analytics(): IO[AppError, Analytics] =
          db.query {
            val users = sql"SELECT COUNT(*) FROM users".query[Long].run().head
            val transfers =
              sql"SELECT COUNT(*) FROM transfers".query[Long].run().head
            val vol = sql"SELECT COALESCE(SUM(amount_minor),0) FROM transfers"
              .query[Long]
              .run()
              .head
            val flagged =
              sql"SELECT COUNT(*) FROM transfers WHERE risk_flag = TRUE"
                .query[Long]
                .run()
                .head
            val openTickets =
              sql"SELECT COUNT(*) FROM support_tickets WHERE status IN ('OPEN','IN_PROGRESS')"
                .query[Long]
                .run()
                .head

            Analytics(users, transfers, vol, flagged, openTickets)
          }

        override def audit(limit: Int): IO[AppError, List[AuditEvent]] =
          db.query { AuditRepo.listLatest(limit) }

        override def flaggedTransfers(
            limit: Int
        ): IO[AppError, List[Transfer]] =
          db.query { TransferRepo.listFlagged(limit) }
      }
    }
}
