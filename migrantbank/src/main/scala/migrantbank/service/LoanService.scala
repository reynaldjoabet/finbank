package migrantbank.service

import migrantbank.db.Db
import migrantbank.domain.*
import migrantbank.repo.*
import zio.*
import java.time.{LocalDate, ZoneId}
import java.util.UUID

trait LoanService {
  def quote(userId: UUID): IO[AppError, Money]
  def request(
      userId: UUID,
      amount: Money,
      correlationId: String
  ): IO[AppError, Loan]
  def list(userId: UUID): IO[AppError, List[Loan]]
}

object LoanService {

  val live: ZLayer[Db, Nothing, LoanService] =
    ZLayer.fromFunction { (db: Db) =>
      new LoanService {

        private val Percent = 0.30
        private val CapMinor = 200000L // $2,000 demo cap

        override def quote(userId: UUID): IO[AppError, Money] =
          db.query {
            TransferRepo.sumOutgoingVolume(userId)
          }.map { vol =>
            val max = math.min((vol * Percent).toLong, CapMinor)
            Money(max, "USD")
          }

        override def request(
            userId: UUID,
            amount: Money,
            correlationId: String
        ): IO[AppError, Loan] =
          if amount.amountMinor <= 0 then
            ZIO.fail(AppError.Validation("Amount must be > 0"))
          else
            for {
              q <- quote(userId)
              _ <- ZIO
                .fail(AppError.Validation("Currency mismatch"))
                .when(q.currency != amount.currency)
              _ <- ZIO
                .fail(
                  AppError.Validation(
                    s"Amount exceeds eligible max: ${q.amountMinor}"
                  )
                )
                .when(amount.amountMinor > q.amountMinor)

              now <- Clock.instant
              // Pure way to get LocalDate from ZIO Clock
              today <- Clock.localDateTime.map(_.toLocalDate)
              due = today.plusDays(30).toString

              id <- Random.nextUUID
              fee = math.max(1L, (amount.amountMinor * 0.03).toLong)
              loan = Loan(id, userId, amount, fee, due, LoanStatus.ACTIVE, now)

              userAcc <- db
                .query {
                  AccountRepo.findByUserForUpdate(userId)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(AppError.NotFound("Account not found"))
                }
              fundAcc <- db
                .query {
                  AccountRepo.getByIdForUpdate(SystemAccounts.LoanFund)
                }
                .flatMap {
                  ZIO
                    .fromOption(_)
                    .orElseFail(
                      AppError.NotFound("Loan fund account not found")
                    )
                }

              _ <- ZIO
                .fail(AppError.Validation("Currency mismatch"))
                .when(
                  userAcc.currency != amount.currency || fundAcc.currency != amount.currency
                )
              _ <- ZIO
                .fail(AppError.Validation("Loan fund depleted"))
                .when(fundAcc.balanceMinor < amount.amountMinor)

              _ <- db.transaction {
                AccountRepo.updateBalance(
                  fundAcc.id,
                  fundAcc.balanceMinor - amount.amountMinor
                )
                AccountRepo.updateBalance(
                  userAcc.id,
                  userAcc.balanceMinor + amount.amountMinor
                )

                LoanRepo.insert(loan)

                LedgerRepo.insert(
                  fundAcc.id,
                  userAcc.id,
                  amount.amountMinor,
                  amount.currency,
                  s"Loan disbursement ${loan.id}"
                )

                AuditRepo.append(
                  "loan_issued",
                  Some(userId),
                  correlationId,
                  s"principal=${amount.amountMinor} fee=$fee due=$due"
                )
              }
            } yield loan

        override def list(userId: UUID): IO[AppError, List[Loan]] =
          db.query {
            LoanRepo.listByUser(userId)
          }
      }
    }
}
