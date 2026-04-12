package migrantbank.service

import migrantbank.db.Db
import migrantbank.repo.AccountRepo
import migrantbank.domain.*
import zio.*

import java.util.UUID

trait AccountService {
  def me(userId: UUID): IO[AppError, Account]
}

object AccountService {
  val live: ZLayer[Db, Nothing, AccountService] =
    ZLayer.fromFunction { (db: Db) =>
      new AccountService {
        override def me(userId: UUID): IO[AppError, Account] =
          db.query {
            AccountRepo.getUserAccount(userId)
          }.flatMap {
            case Some(a) =>
              ZIO.succeed(
                Account(
                  a.id,
                  a.userId,
                  a.accountType,
                  a.name,
                  a.currency,
                  a.balanceMinor,
                  a.createdAt
                )
              )
            case None =>
              ZIO.fail(AppError.NotFound(s"Account for user $userId not found"))
          }
      }
    }
}
