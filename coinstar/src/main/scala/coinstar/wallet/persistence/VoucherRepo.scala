package coinstar.wallet.persistence

import coinstar.wallet.domain.DomainError
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

import java.time.Instant

trait VoucherRepo {
  def find(code: String): IO[DomainError, Option[KioskVoucherRow]]
  def markRedeemed(
      code: String,
      userId: java.util.UUID,
      at: Instant
  ): IO[DomainError, Unit]
}
object VoucherRepo {
  def find(
      code: String
  ): ZIO[VoucherRepo, DomainError, Option[KioskVoucherRow]] =
    ZIO.serviceWithZIO[VoucherRepo](_.find(code))

  def markRedeemed(
      code: String,
      userId: java.util.UUID,
      at: Instant
  ): ZIO[VoucherRepo, DomainError, Unit] =
    ZIO.serviceWithZIO[VoucherRepo](_.markRedeemed(code, userId, at))
}
final class VoucherRepoLive(quill: Quill.Postgres[SnakeCase])
    extends VoucherRepo {
  import quill.*

  private inline def vouchers = quote(
    querySchema[KioskVoucherRow]("kiosk_vouchers")
  )

  override def find(code: String): IO[DomainError, Option[KioskVoucherRow]] =
    run(vouchers.filter(_.code == lift(code)))
      .map(_.headOption)
      .mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))

  override def markRedeemed(
      code: String,
      userId: java.util.UUID,
      at: Instant
  ): IO[DomainError, Unit] =
    run(
      vouchers
        .filter(v => v.code == lift(code) && v.redeemedAt.isEmpty)
        .update(
          v => v.redeemedBy -> lift(Option(userId)),
          v => v.redeemedAt -> lift(Option(at))
        )
    ).mapError(e => DomainError.External(s"DB error: ${e.getMessage}"))
      .flatMap { updated =>
        if updated == 1 then ZIO.unit
        else ZIO.fail(DomainError.Conflict("Voucher already redeemed"))
      }
}
object VoucherRepoLive {
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, VoucherRepo] =
    ZLayer.fromFunction(new VoucherRepoLive(_))
}
