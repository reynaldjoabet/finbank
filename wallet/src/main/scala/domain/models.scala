package domain

import zio.json.*
import java.time.Instant
import java.util.UUID

opaque type MerchantId = UUID
object MerchantId {
  def apply(uuid: UUID): MerchantId = uuid
  def unapply(id: MerchantId): UUID = id
  def random: MerchantId = UUID.randomUUID()
  def fromUUID(uuid: UUID): MerchantId = uuid

  given CanEqual[MerchantId, MerchantId] = CanEqual.derived
  given JsonCodec[MerchantId] = JsonCodec.uuid
}

opaque type InvoiceId = UUID
object InvoiceId {
  def apply(uuid: UUID): InvoiceId = uuid
  def unapply(id: InvoiceId): UUID = id
  def random: InvoiceId = UUID.randomUUID()
  def fromUUID(uuid: UUID): InvoiceId = uuid

  given CanEqual[InvoiceId, InvoiceId] = CanEqual.derived
  given JsonCodec[InvoiceId] = JsonCodec.uuid
}

opaque type PaymentId = UUID
object PaymentId {
  def apply(uuid: UUID): PaymentId = uuid
  def unapply(id: PaymentId): UUID = id
  def random: PaymentId = UUID.randomUUID()
  def fromUUID(uuid: UUID): PaymentId = uuid

  given CanEqual[PaymentId, PaymentId] = CanEqual.derived
  given JsonCodec[PaymentId] = JsonCodec.uuid
}

final case class Money(amountMinor: Long, currency: String)
object Money {
  given JsonCodec[Money] = DeriveJsonCodec.gen[Money]
}

enum Provider derives CanEqual {
  case MtnMomo, OrangeMoney, MPesa, AirtelMoney, Sandbox
}
object Provider {
  given JsonCodec[Provider] = DeriveJsonCodec.gen[Provider]
}

enum InvoiceStatus derives CanEqual {
  case Unpaid, PartiallyPaid, Paid, Cancelled
}
object InvoiceStatus { given JsonCodec[InvoiceStatus] = DeriveJsonCodec.gen }

enum PaymentStatus derives CanEqual {
  case Initiated, PendingProvider, Succeeded, Failed
}
object PaymentStatus { given JsonCodec[PaymentStatus] = DeriveJsonCodec.gen }

final case class Invoice(
    id: InvoiceId,
    merchantId: MerchantId,
    reference: String, // merchant-visible reference
    customerMsisdn: String, // phone number (E.164 recommended)
    total: Money,
    paid: Money,
    status: InvoiceStatus,
    createdAt: Instant
)
object Invoice {
  given JsonCodec[Invoice] = DeriveJsonCodec.gen[Invoice]
}

final case class PaymentIntent(
    id: PaymentId,
    invoiceId: InvoiceId,
    merchantId: MerchantId,
    provider: Provider,
    amount: Money,
    externalRef: Option[String], // provider reference (after initiation)
    status: PaymentStatus,
    idempotencyKey: String,
    createdAt: Instant
)
object PaymentIntent {
  given JsonCodec[PaymentIntent] = DeriveJsonCodec.gen[PaymentIntent]
}

sealed trait AppError extends Throwable
object AppError {
  final case class NotFound(msg: String) extends Exception(msg) with AppError
  final case class Validation(msg: String) extends Exception(msg) with AppError
  final case class ProviderError(msg: String)
      extends Exception(msg)
      with AppError
  final case class Conflict(msg: String) extends Exception(msg) with AppError
}
