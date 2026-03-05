package coinstar.wallet.service

import coinstar.wallet.domain.*
import coinstar.wallet.persistence.{LedgerRepo, VoucherRepo, WalletRepo}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

import java.time.Instant

trait WalletService {
  def listWallets(userId: UserId): IO[DomainError, List[Wallet]]
  def createWallet(userId: UserId, asset: Asset): IO[DomainError, Wallet]
  def getWallet(userId: UserId, walletId: WalletId): IO[DomainError, Wallet]
  def redeemVoucher(
      userId: UserId,
      walletId: WalletId,
      code: String
  ): IO[DomainError, LedgerTxId]
  def readiness: IO[DomainError, Unit]
}
object WalletService {
  def listWallets(
      userId: UserId
  ): ZIO[WalletService, DomainError, List[Wallet]] =
    ZIO.serviceWithZIO[WalletService](_.listWallets(userId))

  def createWallet(
      userId: UserId,
      asset: Asset
  ): ZIO[WalletService, DomainError, Wallet] =
    ZIO.serviceWithZIO[WalletService](_.createWallet(userId, asset))

  def getWallet(
      userId: UserId,
      walletId: WalletId
  ): ZIO[WalletService, DomainError, Wallet] =
    ZIO.serviceWithZIO[WalletService](_.getWallet(userId, walletId))

  def redeemVoucher(
      userId: UserId,
      walletId: WalletId,
      code: String
  ): ZIO[WalletService, DomainError, LedgerTxId] =
    ZIO.serviceWithZIO[WalletService](_.redeemVoucher(userId, walletId, code))

  def readiness: ZIO[WalletService, DomainError, Unit] =
    ZIO.serviceWithZIO[WalletService](_.readiness)
}
final class WalletServiceLive(
    quill: Quill.Postgres[SnakeCase],
    walletRepo: WalletRepo,
    ledgerRepo: LedgerRepo,
    voucherRepo: VoucherRepo
) extends WalletService {
  import quill.*

  override def listWallets(userId: UserId): IO[DomainError, List[Wallet]] =
    walletRepo.listByUser(userId)

  override def createWallet(
      userId: UserId,
      asset: Asset
  ): IO[DomainError, Wallet] =
    walletRepo.createIfMissing(userId, asset)

  override def getWallet(
      userId: UserId,
      walletId: WalletId
  ): IO[DomainError, Wallet] =
    walletRepo.findById(userId, walletId)

  override def readiness: IO[DomainError, Unit] =
    walletRepo.ping

  /** Voucher redeem = atomic credit of a wallet + voucher status update +
    * ledger tx recording.
    *
    * In production you likely want:
    *   - idempotency enforcement on the *API* for this endpoint
    *   - risk checks / AML / velocity limits
    *   - auditing + immutable append-only ledger
    */
  override def redeemVoucher(
      userId: UserId,
      walletId: WalletId,
      code: String
  ): IO[DomainError, LedgerTxId] =

    (for {
      wallet <- walletRepo.findById(userId, walletId)
      voucherOpt <- voucherRepo.find(code)
      voucher <- ZIO
        .fromOption(voucherOpt)
        .mapError(_ => DomainError.NotFound("Voucher not found"))
      _ <- ZIO
        .fail(DomainError.Validation("Voucher expired"))
        .when(voucher.expiresAt.isBefore(Instant.now()))
      _ <- ZIO
        .fail(DomainError.Conflict("Voucher already redeemed"))
        .when(voucher.redeemedAt.isDefined)
      _ <- ZIO
        .fail(
          DomainError.Validation("Voucher asset does not match wallet asset")
        )
        .when(voucher.asset != wallet.asset.code)

      txId <- ledgerRepo.createTx(userId, kind = "KIOSK_VOUCHER_REDEEM")
      _ <- ledgerRepo.addEntry(
        txId,
        walletId,
        wallet.asset.code,
        deltaMinor = voucher.amountMinor
      )

      // Wallet optimistic update (bounded retry recommended)
      newBalance = wallet.balanceMinor + voucher.amountMinor
      _ <- walletRepo.updateBalance(
        walletId,
        expectedVersion = wallet.version,
        newBalanceMinor = newBalance
      )

      _ <- voucherRepo.markRedeemed(code, userId.value, Instant.now())
    } yield txId)
}
object WalletServiceLive {
  val layer: ZLayer[
    Quill.Postgres[SnakeCase] & WalletRepo & LedgerRepo & VoucherRepo,
    Nothing,
    WalletService
  ] =
    ZLayer.fromFunction(new WalletServiceLive(_, _, _, _))

}
