package repo
import domain.*
import zio.*
import java.time.Instant

trait InvoiceRepo {
  def create(invoice: Invoice): IO[AppError, Unit]
  def get(id: InvoiceId): IO[AppError, Invoice]
  def update(invoice: Invoice): IO[AppError, Unit]
}

trait PaymentRepo {
  def create(intent: PaymentIntent): IO[AppError, Unit]

  /** Must be idempotent on (merchantId, idempotencyKey). */
  def getByIdempotency(
      merchantId: MerchantId,
      idempotencyKey: String
  ): IO[AppError, Option[PaymentIntent]]

  /** Must be unique on provider external transaction id/ref. */
  def getByExternalRef(
      provider: Provider,
      externalRef: String
  ): IO[AppError, Option[PaymentIntent]]

  def update(intent: PaymentIntent): IO[AppError, Unit]
}

enum LedgerEntryType { case Debit, Credit }

final case class LedgerEntry(
    merchantId: MerchantId,
    invoiceId: InvoiceId,
    paymentId: PaymentId,
    entryType: LedgerEntryType,
    amount: Money,
    createdAt: Instant
)

trait LedgerRepo {
  def append(entry: LedgerEntry): IO[AppError, Unit]
}