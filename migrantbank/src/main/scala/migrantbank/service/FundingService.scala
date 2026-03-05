package com.migrantbank.service

import com.migrantbank.db.Db
import com.migrantbank.domain.*
import com.migrantbank.repo.*
import zio.*
import java.util.UUID

trait FundingService {
  def topUp(
      userId: UUID,
      amount: Money,
      source: String,
      correlationId: String
  ): IO[AppError, Account]
  def cashDeposit(
      userId: UUID,
      amount: Money,
      branchRef: String,
      correlationId: String
  ): IO[AppError, Account]
}

object FundingService {

  val live: ZLayer[Db, Nothing, FundingService] =
    ZLayer.fromFunction { (db: Db) =>
      new FundingService {

        override def topUp(
            userId: UUID,
            amount: Money,
            source: String,
            correlationId: String
        ): IO[AppError, Account] =
          if amount.amountMinor <= 0 then
            ZIO.fail(AppError.Validation("Amount must be > 0"))
          else if source.trim.isEmpty then
            ZIO.fail(AppError.Validation("Source required"))
          else
            for {
              userAcc <- db
                .query {
                  AccountRepo.findByUserForUpdate(userId)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("Account not found"))
                }
              sysAcc <- db
                .query {
                  AccountRepo.getByIdForUpdate(SystemAccounts.TopupClearing)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("System account not found"))
                }

              _ <- ZIO
                .fail(AppError.Validation("Currency mismatch"))
                .when(
                  userAcc.currency != amount.currency || sysAcc.currency != amount.currency
                )

              newUserBal = userAcc.balanceMinor + amount.amountMinor
              newSysBal = sysAcc.balanceMinor - amount.amountMinor

              transferId <- Random.nextUUID
              now <- Clock.instant

              _ <- db.transaction {
                AccountRepo.updateBalance(userAcc.id, newUserBal)
                AccountRepo.updateBalance(sysAcc.id, newSysBal)

                val t = Transfer(
                  id = transferId,
                  transferType = TransferType.ACH,
                  fromUserId = userId,
                  toUserId = None,
                  achDestination = Some(s"TOPUP:$source"),
                  amount = amount,
                  note = Some("Top-up"),
                  status = TransferStatus.COMPLETED,
                  idempotencyKey = None,
                  riskFlag = false,
                  riskReason = None,
                  createdAt = now
                )

                TransferRepo.insert(t)
                LedgerRepo.insert(
                  sysAcc.id,
                  userAcc.id,
                  amount.amountMinor,
                  amount.currency,
                  "Top-up debit from clearing"
                )
                AuditRepo.append(
                  "funding_topup",
                  Some(userId),
                  correlationId,
                  s"amount=${amount.amountMinor} ${amount.currency}, source=$source"
                )
              }
            } yield Account(
              userAcc.id,
              userAcc.userId,
              userAcc.accountType,
              userAcc.name,
              userAcc.currency,
              newUserBal,
              userAcc.createdAt
            )

        override def cashDeposit(
            userId: UUID,
            amount: Money,
            branchRef: String,
            correlationId: String
        ): IO[AppError, Account] =
          if amount.amountMinor <= 0 then
            ZIO.fail(AppError.Validation("Amount must be > 0"))
          else if branchRef.trim.isEmpty then
            ZIO.fail(AppError.Validation("Branch reference required"))
          else
            for {
              userAcc <- db
                .query {
                  AccountRepo.findByUserForUpdate(userId)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("Account not found"))
                }
              sysAcc <- db
                .query {
                  AccountRepo.getByIdForUpdate(SystemAccounts.CashClearing)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("System account not found"))
                }

              _ <- ZIO
                .fail(AppError.Validation("Currency mismatch"))
                .when(
                  userAcc.currency != amount.currency || sysAcc.currency != amount.currency
                )

              newUserBal = userAcc.balanceMinor + amount.amountMinor
              newSysBal = sysAcc.balanceMinor - amount.amountMinor

              transferId <- Random.nextUUID
              now <- Clock.instant

              _ <- db.transaction {
                AccountRepo.updateBalance(userAcc.id, newUserBal)
                AccountRepo.updateBalance(sysAcc.id, newSysBal)

                val t = Transfer(
                  id = transferId,
                  transferType = TransferType.ACH,
                  fromUserId = userId,
                  toUserId = None,
                  achDestination = Some(s"CASH_DEPOSIT:$branchRef"),
                  amount = amount,
                  note = Some("Cash deposit"),
                  status = TransferStatus.COMPLETED,
                  idempotencyKey = None,
                  riskFlag = false,
                  riskReason = None,
                  createdAt = now
                )

                TransferRepo.insert(t)
                LedgerRepo.insert(
                  sysAcc.id,
                  userAcc.id,
                  amount.amountMinor,
                  amount.currency,
                  "Cash deposit"
                )
                AuditRepo.append(
                  "funding_cash_deposit",
                  Some(userId),
                  correlationId,
                  s"amount=${amount.amountMinor} ${amount.currency}, branchRef=$branchRef"
                )
              }
            } yield Account(
              userAcc.id,
              userAcc.userId,
              userAcc.accountType,
              userAcc.name,
              userAcc.currency,
              newUserBal,
              userAcc.createdAt
            )
      }
    }
}
