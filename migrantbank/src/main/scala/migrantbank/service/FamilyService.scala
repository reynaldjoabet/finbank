package com.migrantbank.service

import com.migrantbank.db.Db
import com.migrantbank.domain.{*, given}
import com.migrantbank.repo.*
import zio.*
import java.util.UUID

trait FamilyService {
  def create(
      owner: UUID,
      members: Set[UUID],
      correlationId: String
  ): IO[AppError, FamilyGroup]
  def list(owner: UUID): IO[AppError, List[FamilyGroup]]
  def distribute(
      owner: UUID,
      groupId: UUID,
      payouts: Map[UUID, Money],
      correlationId: String
  ): IO[AppError, List[Transfer]]
}

object FamilyService {

  val live: ZLayer[Db, Nothing, FamilyService] =
    ZLayer.fromFunction { (db: Db) =>
      new FamilyService {

        override def create(
            owner: UUID,
            members: Set[UUID],
            correlationId: String
        ): IO[AppError, FamilyGroup] =
          if members.isEmpty then
            ZIO.fail(AppError.Validation("At least 1 member required"))
          else
            db.transaction {
              val group = FamilyRepo.create(owner, members)
              AuditRepo.append(
                "family_group_created",
                Some(owner),
                correlationId,
                s"members=${members.size}"
              )
              group
            }

        override def list(owner: UUID): IO[AppError, List[FamilyGroup]] =
          db.query {
            FamilyRepo.listByOwner(owner)
          }

        override def distribute(
            owner: UUID,
            groupId: UUID,
            payouts: Map[UUID, Money],
            correlationId: String
        ): IO[AppError, List[Transfer]] =
          if payouts.isEmpty then ZIO.fail(AppError.Validation("No payouts"))
          else
            for {
              group <- db.query { FamilyRepo.get(groupId) }.flatMap {
                ZIO
                  .fromOption(_)
                  .orElseFail(
                    AppError.NotFound(s"Family group $groupId not found")
                  )
              }
              _ <- ZIO
                .fail(AppError.Forbidden("Only owner can distribute"))
                .when(group.ownerUserId != owner)

              // Validation logic
              _ <- ZIO.foreachDiscard(payouts.toList) { case (uid, m) =>
                for {
                  _ <- ZIO
                    .fail(AppError.Validation(s"Recipient $uid not in group"))
                    .unless(group.memberUserIds.contains(uid))
                  _ <- ZIO
                    .fail(AppError.Validation("Amount must be > 0"))
                    .when(m.amountMinor <= 0)
                } yield ()
              }

              currency = payouts.head._2.currency
              _ <- ZIO.foreachDiscard(payouts.values) { m =>
                ZIO
                  .fail(AppError.Validation("Currency mismatch"))
                  .when(m.currency != currency)
              }

              total = payouts.values.map(_.amountMinor).sum

              // Lock owner account for update
              ownerAcc <- db
                .query {
                  AccountRepo.findByUserForUpdate(owner)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("Owner account not found"))
                }

              _ <- ZIO
                .fail(AppError.Validation("Currency mismatch"))
                .when(ownerAcc.currency != currency)
              _ <- ZIO
                .fail(AppError.Validation("Insufficient funds"))
                .when(ownerAcc.balanceMinor < total)

              // Lock and validate recipient accounts
              recipientIds = payouts.keys.toList.sortBy(_.toString)
              recipientAccs <- ZIO.foreach(recipientIds) { uid =>
                db.query { AccountRepo.findByUserForUpdate(uid) }.flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(
                      AppError.NotFound(s"Recipient account not found: $uid")
                    )
                }
              }

              _ <- ZIO.foreachDiscard(recipientAccs) { a =>
                ZIO
                  .fail(AppError.Validation("Currency mismatch"))
                  .when(a.currency != currency)
              }

              // Generate transfer data outside transaction
              transferData <- ZIO.foreach(payouts.toList) { case (toUser, m) =>
                for {
                  id <- Random.nextUUID
                  now <- Clock.instant
                } yield (id, toUser, m, now)
              }

              // Execute Balances Update and create transfers in transaction
              transfers <- db.transaction {
                AccountRepo.updateBalance(
                  ownerAcc.id,
                  ownerAcc.balanceMinor - total
                )
                recipientAccs.foreach { acc =>
                  val uid = acc.userId.get
                  val amt = payouts(uid).amountMinor
                  AccountRepo.updateBalance(acc.id, acc.balanceMinor + amt)
                }

                // Generate Transfers and Ledger entries
                val transfers = transferData.map { case (id, toUser, m, now) =>
                  val t = Transfer(
                    id = id,
                    transferType = TransferType.P2P,
                    fromUserId = owner,
                    toUserId = Some(toUser),
                    achDestination = None,
                    amount = m,
                    note = Some(s"Family Mode group=$groupId"),
                    status = TransferStatus.COMPLETED,
                    idempotencyKey = None,
                    riskFlag = false,
                    riskReason = None,
                    createdAt = now
                  )
                  TransferRepo.insert(t)
                  val toAcc = recipientAccs.find(_.userId.contains(toUser)).get
                  LedgerRepo.insert(
                    ownerAcc.id,
                    toAcc.id,
                    m.amountMinor,
                    m.currency,
                    s"P2P transfer to $toUser"
                  )
                  t
                }

                AuditRepo.append(
                  "family_distribute",
                  Some(owner),
                  correlationId,
                  s"groupId=$groupId transfers=${transfers.size} total=$total $currency"
                )
                transfers
              }
            } yield transfers
      }
    }
}
