package coinstar.wallet.persistence

import coinstar.wallet.domain.{Asset, DomainError, UserId, Wallet, WalletId}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

import java.time.Instant
import java.util.UUID
import java.sql.SQLException

trait WalletRepo {
  def findById(userId: UserId, walletId: WalletId): IO[DomainError, Wallet]
  def findByUserAndAsset(
      userId: UserId,
      asset: Asset
  ): IO[DomainError, Option[Wallet]]
  def listByUser(userId: UserId): IO[DomainError, List[Wallet]]
  def createIfMissing(userId: UserId, asset: Asset): IO[DomainError, Wallet]

  /** Optimistic concurrency: update must match expected version. */
  def updateBalance(
      walletId: WalletId,
      expectedVersion: Long,
      newBalanceMinor: Long
  ): IO[DomainError, Unit]

  def ping: IO[DomainError, Unit]
}
object WalletRepo {
  def findById(
      userId: UserId,
      walletId: WalletId
  ): ZIO[WalletRepo, DomainError, Wallet] =
    ZIO.serviceWithZIO[WalletRepo](_.findById(userId, walletId))

  def listByUser(userId: UserId): ZIO[WalletRepo, DomainError, List[Wallet]] =
    ZIO.serviceWithZIO[WalletRepo](_.listByUser(userId))

  def createIfMissing(
      userId: UserId,
      asset: Asset
  ): ZIO[WalletRepo, DomainError, Wallet] =
    ZIO.serviceWithZIO[WalletRepo](_.createIfMissing(userId, asset))

  def updateBalance(
      walletId: WalletId,
      expectedVersion: Long,
      newBalanceMinor: Long
  ): ZIO[WalletRepo, DomainError, Unit] =
    ZIO.serviceWithZIO[WalletRepo](
      _.updateBalance(walletId, expectedVersion, newBalanceMinor)
    )

  def ping: ZIO[WalletRepo, DomainError, Unit] =
    ZIO.serviceWithZIO[WalletRepo](_.ping)
}
final class WalletRepoLive(quill: Quill.Postgres[SnakeCase])
    extends WalletRepo {
  import quill.*

  private inline def wallets = quote(querySchema[WalletRow]("wallets"))

  private def toDomain(row: WalletRow): IO[DomainError, Wallet] =
    Asset.fromCode(row.asset) match {
      case Left(err) =>
        ZIO.fail(
          DomainError.Internal(
            s"DB contains unsupported asset '${row.asset}': $err"
          )
        )
      case Right(asset) =>
        ZIO.succeed(
          Wallet(
            id = WalletId(row.id),
            userId = UserId(row.userId),
            asset = asset,
            balanceMinor = row.balanceMinor,
            version = row.version,
            createdAt = row.createdAt
          )
        )
    }

  override def ping: IO[DomainError, Unit] =
    run(quote(infix"SELECT 1".as[Query[Int]])).unit
      .mapError(e => DomainError.External(s"DB ping failed: ${e.getMessage}"))

  override def findById(
      userId: UserId,
      walletId: WalletId
  ): IO[DomainError, Wallet] =
    run(
      wallets.filter(w =>
        w.id == lift(walletId.value) && w.userId == lift(userId.value)
      )
    ).map(_.headOption)
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .flatMap {
        case None      => ZIO.fail(DomainError.NotFound("Wallet not found"))
        case Some(row) => toDomain(row)
      }

  override def findByUserAndAsset(
      userId: UserId,
      asset: Asset
  ): IO[DomainError, Option[Wallet]] =
    run(
      wallets.filter(w =>
        w.userId == lift(userId.value) && w.asset == lift(asset.code)
      )
    ).map(_.headOption)
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .flatMap(ZIO.foreach(_)(toDomain))

  override def listByUser(userId: UserId): IO[DomainError, List[Wallet]] =
    run(wallets.filter(_.userId == lift(userId.value)))
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .flatMap(rows => ZIO.foreach(rows)(toDomain))

  override def createIfMissing(
      userId: UserId,
      asset: Asset
  ): IO[DomainError, Wallet] =
    findByUserAndAsset(userId, asset).flatMap {
      case Some(w) => ZIO.succeed(w)
      case None    =>
        val now = Instant.now()
        val id = UUID.randomUUID()
        val row = WalletRow(
          id,
          userId.value,
          asset.code,
          balanceMinor = 0L,
          version = 0L,
          createdAt = now
        )
        run(wallets.insertValue(lift(row)))
          .mapError { case e: SQLException =>
            DomainError.External(s"DB insert failed: ${e.getMessage}")

          }
          .zipRight(findById(userId, WalletId(id)))
    }

  override def updateBalance(
      walletId: WalletId,
      expectedVersion: Long,
      newBalanceMinor: Long
  ): IO[DomainError, Unit] =
    run(
      wallets
        .filter(w =>
          w.id == lift(walletId.value) && w.version == lift(expectedVersion)
        )
        .update(
          _.balanceMinor -> lift(newBalanceMinor),
          _.version -> (lift(expectedVersion) + 1L)
        )
    ).mapError(e => DomainError.External(s"DB update failed: ${e.getMessage}"))
      .flatMap { updatedRows =>
        if updatedRows == 1 then ZIO.unit
        else
          ZIO.fail(
            DomainError
              .Conflict("Concurrent update detected (wallet version mismatch)")
          )
      }
}
object WalletRepoLive {
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, WalletRepo] =
    ZLayer.fromFunction(new WalletRepoLive(_))
}
