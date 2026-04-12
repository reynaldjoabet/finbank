package migrantbank.service

import migrantbank.config.AppConfig
import migrantbank.db.Db
import migrantbank.domain.{*, given}
import migrantbank.repo.*
import zio.*
import java.util.UUID

trait TransferService {
  def p2p(
      from: UUID,
      to: UUID,
      amount: Money,
      note: Option[String],
      idempotencyKey: Option[String],
      correlationId: String
  ): IO[AppError, Transfer]
  def ach(
      from: UUID,
      destination: String,
      amount: Money,
      note: Option[String],
      idempotencyKey: Option[String],
      correlationId: String
  ): IO[AppError, Transfer]
  def list(userId: UUID): IO[AppError, List[Transfer]]
  def adminSetStatus(
      transferId: UUID,
      status: TransferStatus,
      correlationId: String
  ): IO[AppError, Unit]
}

object TransferService {

  val live: ZLayer[Db & AppConfig, Nothing, TransferService] =
    ZLayer.fromFunction { (db: Db, cfg: AppConfig) =>
      new TransferService {

        private def risk(amount: Money): (Boolean, Option[String]) =
          if amount.amountMinor >= cfg.fraud.flagThresholdMinor then
            (true, Some("THRESHOLD_EXCEEDED"))
          else (false, None)

        override def p2p(
            from: UUID,
            to: UUID,
            amount: Money,
            note: Option[String],
            idempotencyKey: Option[String],
            correlationId: String
        ): IO[AppError, Transfer] =
          if from == to then
            ZIO.fail(AppError.Validation("Cannot transfer to self"))
          else if amount.amountMinor <= 0 then
            ZIO.fail(AppError.Validation("Amount must be > 0"))
          else
            for {
              existing <- idempotencyKey match {
                case Some(k) =>
                  db.query {
                    TransferRepo.findByIdempotency(from, TransferType.P2P, k)
                  }
                case None => ZIO.succeed(None)
              }

              t <- existing match {
                case Some(found) => ZIO.succeed(found)
                case None        =>
                  for {
                    fromAcc <- db
                      .query {
                        AccountRepo.findByUserForUpdate(from)
                      }
                      .flatMap {
                        ZIO
                          .fromOption(_)
                          .orElseFail(
                            AppError.NotFound("Sender account not found")
                          )
                      }
                    toAcc <- db
                      .query {
                        AccountRepo.findByUserForUpdate(to)
                      }
                      .flatMap {
                        ZIO
                          .fromOption(_)
                          .orElseFail(
                            AppError.NotFound("Recipient account not found")
                          )
                      }

                    _ <- ZIO
                      .fail(AppError.Validation("Currency mismatch"))
                      .when(
                        fromAcc.currency != amount.currency || toAcc.currency != amount.currency
                      )
                    _ <- ZIO
                      .fail(AppError.Validation("Insufficient funds"))
                      .when(fromAcc.balanceMinor < amount.amountMinor)

                    id <- Random.nextUUID
                    now <- Clock.instant
                    (flag, reason) = risk(amount)
                    transfer = Transfer(
                      id = id,
                      transferType = TransferType.P2P,
                      fromUserId = from,
                      toUserId = Some(to),
                      achDestination = None,
                      amount = amount,
                      note = note,
                      status = TransferStatus.COMPLETED,
                      idempotencyKey = idempotencyKey,
                      riskFlag = flag,
                      riskReason = reason,
                      createdAt = now
                    )

                    _ <- db.transaction {
                      AccountRepo.updateBalance(
                        fromAcc.id,
                        fromAcc.balanceMinor - amount.amountMinor
                      )
                      AccountRepo.updateBalance(
                        toAcc.id,
                        toAcc.balanceMinor + amount.amountMinor
                      )

                      TransferRepo.insert(transfer)
                      LedgerRepo.insert(
                        fromAcc.id,
                        toAcc.id,
                        amount.amountMinor,
                        amount.currency,
                        s"P2P transfer $id"
                      )
                      AuditRepo.append(
                        "transfer_p2p",
                        Some(from),
                        correlationId,
                        s"to=$to amount=${amount.amountMinor} ${amount.currency} flag=$flag"
                      )
                    }
                  } yield transfer
              }
            } yield t

        override def ach(
            from: UUID,
            destination: String,
            amount: Money,
            note: Option[String],
            idempotencyKey: Option[String],
            correlationId: String
        ): IO[AppError, Transfer] =
          if destination.trim.isEmpty then
            ZIO.fail(AppError.Validation("Destination required"))
          else if amount.amountMinor <= 0 then
            ZIO.fail(AppError.Validation("Amount must be > 0"))
          else
            for {
              existing <- idempotencyKey match {
                case Some(k) =>
                  db.query {
                    TransferRepo.findByIdempotency(from, TransferType.ACH, k)
                  }
                case None => ZIO.succeed(None)
              }

              t <- existing match {
                case Some(found) => ZIO.succeed(found)
                case None        =>
                  for {
                    fromAcc <- db
                      .query {
                        AccountRepo.findByUserForUpdate(from)
                      }
                      .flatMap {
                        ZIO
                          .fromOption(_)
                          .orElseFail(
                            AppError.NotFound("Sender account not found")
                          )
                      }
                    clearing <- db
                      .query {
                        AccountRepo.getByIdForUpdate(SystemAccounts.AchClearing)
                      }
                      .flatMap {
                        ZIO
                          .fromOption(_)
                          .orElseFail(
                            AppError.NotFound("Clearing account not found")
                          )
                      }

                    _ <- ZIO
                      .fail(AppError.Validation("Currency mismatch"))
                      .when(
                        fromAcc.currency != amount.currency || clearing.currency != amount.currency
                      )
                    _ <- ZIO
                      .fail(AppError.Validation("Insufficient funds"))
                      .when(fromAcc.balanceMinor < amount.amountMinor)

                    id <- Random.nextUUID
                    now <- Clock.instant
                    (flag, reason) = risk(amount)
                    transfer = Transfer(
                      id = id,
                      transferType = TransferType.ACH,
                      fromUserId = from,
                      toUserId = None,
                      achDestination = Some(destination),
                      amount = amount,
                      note = note,
                      status = TransferStatus.PROCESSING,
                      idempotencyKey = idempotencyKey,
                      riskFlag = flag,
                      riskReason = reason,
                      createdAt = now
                    )

                    _ <- db.transaction {
                      AccountRepo.updateBalance(
                        fromAcc.id,
                        fromAcc.balanceMinor - amount.amountMinor
                      )
                      AccountRepo.updateBalance(
                        clearing.id,
                        clearing.balanceMinor + amount.amountMinor
                      )

                      TransferRepo.insert(transfer)
                      LedgerRepo.insert(
                        fromAcc.id,
                        clearing.id,
                        amount.amountMinor,
                        amount.currency,
                        s"ACH transfer $id"
                      )
                      AuditRepo.append(
                        "transfer_ach_created",
                        Some(from),
                        correlationId,
                        s"dest=$destination amount=${amount.amountMinor} ${amount.currency} flag=$flag"
                      )
                    }
                  } yield transfer
              }
            } yield t

        override def list(userId: UUID): IO[AppError, List[Transfer]] =
          db.query {
            TransferRepo.listByUser(userId, limit = 200)
          }

        override def adminSetStatus(
            transferId: UUID,
            status: TransferStatus,
            correlationId: String
        ): IO[AppError, Unit] =
          for {
            t <- db.query { TransferRepo.get(transferId) }
            _ <- (t.status, status) match {
              case (TransferStatus.PROCESSING, TransferStatus.FAILED) =>
                for {
                  clearing <- db
                    .query {
                      AccountRepo.getByIdForUpdate(SystemAccounts.AchClearing)
                    }
                    .flatMap {
                      ZIO
                        .fromOption(_)
                        .orElseFail(
                          AppError.NotFound("Clearing account not found")
                        )
                    }
                  senderAcc <- db
                    .query {
                      AccountRepo.findByUserForUpdate(t.fromUserId)
                    }
                    .flatMap {
                      ZIO
                        .fromOption(_)
                        .orElseFail(
                          AppError.NotFound("Sender account not found")
                        )
                    }
                  _ <- ZIO
                    .fail(AppError.Validation("Currency mismatch"))
                    .when(
                      clearing.currency != t.amount.currency || senderAcc.currency != t.amount.currency
                    )
                  _ <- ZIO
                    .fail(AppError.Validation("Clearing insufficient"))
                    .when(clearing.balanceMinor < t.amount.amountMinor)

                  _ <- db.transaction {
                    AccountRepo.updateBalance(
                      clearing.id,
                      clearing.balanceMinor - t.amount.amountMinor
                    )
                    AccountRepo.updateBalance(
                      senderAcc.id,
                      senderAcc.balanceMinor + t.amount.amountMinor
                    )
                    LedgerRepo.insert(
                      clearing.id,
                      senderAcc.id,
                      t.amount.amountMinor,
                      t.amount.currency,
                      s"Reversal for transfer ${t.id}"
                    )
                  }
                } yield ()
              case _ => ZIO.unit
            }
            _ <- db.transaction {
              TransferRepo.updateStatus(transferId, status)
              AuditRepo.append(
                "admin_transfer_status",
                Some(t.fromUserId),
                correlationId,
                s"transferId=$transferId status=$status"
              )
            }
          } yield ()
      }
    }
}
