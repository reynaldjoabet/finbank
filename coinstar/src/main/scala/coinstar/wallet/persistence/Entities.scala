package coinstar.wallet.persistence

import java.time.Instant
import java.util.UUID

final case class WalletRow(
    id: UUID,
    userId: UUID,
    asset: String,
    balanceMinor: Long,
    version: Long,
    createdAt: Instant
)

final case class LedgerTxRow(
    id: UUID,
    userId: UUID,
    kind: String,
    createdAt: Instant
)

final case class LedgerEntryRow(
    id: UUID,
    txId: UUID,
    walletId: UUID,
    asset: String,
    deltaMinor: Long,
    createdAt: Instant
)

final case class KioskVoucherRow(
    code: String,
    asset: String,
    amountMinor: Long,
    expiresAt: Instant,
    redeemedBy: Option[UUID],
    redeemedAt: Option[Instant]
)

final case class IdempotencyRow(
    userId: UUID,
    idemKey: String,
    requestHash: String,
    responseJson: String,
    createdAt: Instant
)
