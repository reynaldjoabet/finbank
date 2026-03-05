package coinstar.wallet.persistence

import coinstar.wallet.domain.DomainError
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

import java.time.Instant
import java.util.UUID

trait IdempotencyRepo {
  def get(userId: UUID, key: String): IO[DomainError, Option[IdempotencyRow]]
  def put(row: IdempotencyRow): IO[DomainError, Unit]
}
object IdempotencyRepo {
  def get(
      userId: UUID,
      key: String
  ): ZIO[IdempotencyRepo, DomainError, Option[IdempotencyRow]] =
    ZIO.serviceWithZIO[IdempotencyRepo](_.get(userId, key))

  def put(row: IdempotencyRow): ZIO[IdempotencyRepo, DomainError, Unit] =
    ZIO.serviceWithZIO[IdempotencyRepo](_.put(row))
}
final class IdempotencyRepoLive(quill: Quill.Postgres[SnakeCase])
    extends IdempotencyRepo {
  import quill.*

  private inline def table = quote(
    querySchema[IdempotencyRow]("idempotency_keys")
  )

  override def get(
      userId: UUID,
      key: String
  ): IO[DomainError, Option[IdempotencyRow]] =
    run(table.filter(r => r.userId == lift(userId) && r.idemKey == lift(key)))
      .map(_.headOption)
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))

  override def put(row: IdempotencyRow): IO[DomainError, Unit] =
    run(table.insertValue(lift(row)))
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .unit
}
object IdempotencyRepoLive {
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, IdempotencyRepo] =
    ZLayer.fromFunction(new IdempotencyRepoLive(_))
}
