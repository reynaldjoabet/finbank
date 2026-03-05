package domain

import zio.json.*
import java.time.Instant
import java.util.UUID

opaque type MerchantId = UUID
object MerchantId {
  def random: MerchantId = UUID.randomUUID()
  def fromUUID(uuid: UUID): MerchantId = uuid
  given JsonCodec[MerchantId] =
    JsonCodec.uuid.transform[MerchantId](fromUUID, identity)
}

opaque type InvoiceId = UUID
object InvoiceId {
  def random: InvoiceId = UUID.randomUUID()
  def fromUUID(uuid: UUID): InvoiceId = uuid
  given JsonCodec[InvoiceId] =
    JsonCodec.uuid.transform[InvoiceId](fromUUID, identity)
}

opaque type PaymentId = UUID
object PaymentId {
  def random: PaymentId = UUID.randomUUID()
  def fromUUID(uuid: UUID): PaymentId = uuid
  given JsonCodec[PaymentId] =
    JsonCodec.uuid.transform[PaymentId](fromUUID, identity)
}

final case class Money(amountMinor: Long, currency: String)
object Money {
  given JsonCodec[Money] = DeriveJsonCodec.gen[Money]
}

enum Provider {
  case MtnMomo, OrangeMoney, MPesa, AirtelMoney, Sandbox
}
object Provider {
  given JsonCodec[Provider] = DeriveJsonCodec.gen[Provider]
}

enum InvoiceStatus { case Unpaid, PartiallyPaid, Paid, Cancelled }
object InvoiceStatus { given JsonCodec[InvoiceStatus] = DeriveJsonCodec.gen }

enum PaymentStatus { case Initiated, PendingProvider, Succeeded, Failed }
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
