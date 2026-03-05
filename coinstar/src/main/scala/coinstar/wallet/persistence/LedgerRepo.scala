package coinstar.wallet.persistence

import coinstar.wallet.domain.{DomainError, LedgerTxId, UserId, WalletId}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

import java.time.Instant
import java.util.UUID

trait LedgerRepo {
  def createTx(userId: UserId, kind: String): IO[DomainError, LedgerTxId]
  def addEntry(
      txId: LedgerTxId,
      walletId: WalletId,
      asset: String,
      deltaMinor: Long
  ): IO[DomainError, Unit]
}
object LedgerRepo {
  def createTx(
      userId: UserId,
      kind: String
  ): ZIO[LedgerRepo, DomainError, LedgerTxId] =
    ZIO.serviceWithZIO[LedgerRepo](_.createTx(userId, kind))

  def addEntry(
      txId: LedgerTxId,
      walletId: WalletId,
      asset: String,
      deltaMinor: Long
  ): ZIO[LedgerRepo, DomainError, Unit] =
    ZIO.serviceWithZIO[LedgerRepo](
      _.addEntry(txId, walletId, asset, deltaMinor)
    )
}
final class LedgerRepoLive(quill: Quill.Postgres[SnakeCase])
    extends LedgerRepo {
  import quill.*

  private inline def txs = quote(querySchema[LedgerTxRow]("ledger_txs"))
  private inline def entries = quote(
    querySchema[LedgerEntryRow]("ledger_entries")
  )

  override def createTx(
      userId: UserId,
      kind: String
  ): IO[DomainError, LedgerTxId] = {
    val id = UUID.randomUUID()
    val now = Instant.now()
    val row = LedgerTxRow(id, userId.value, kind, now)
    run(txs.insertValue(lift(row)))
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .as(LedgerTxId(id))
  }

  override def addEntry(
      txId: LedgerTxId,
      walletId: WalletId,
      asset: String,
      deltaMinor: Long
  ): IO[DomainError, Unit] = {
    val id = UUID.randomUUID()
    val now = Instant.now()
    val row =
      LedgerEntryRow(id, txId.value, walletId.value, asset, deltaMinor, now)
    run(entries.insertValue(lift(row)))
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .unit
  }
}
object LedgerRepoLive {
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, LedgerRepo] =
    ZLayer.fromFunction(new LedgerRepoLive(_))
}
