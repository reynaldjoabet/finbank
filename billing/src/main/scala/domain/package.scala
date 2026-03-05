import java.util.UUID
import zio.*
import zio.json.*
import java.time.OffsetDateTime
package object domain {

  opaque type InvoiceId = UUID
  object InvoiceId {
    def apply(uuid: UUID): InvoiceId = uuid
    def random: UIO[InvoiceId] = ZIO.succeed(UUID.randomUUID())
  }

  enum PaymentStatus {
    case Pending, Processing, Succeeded, Failed
  }

  case class Invoice(
      id: InvoiceId,
      amount: BigDecimal,
      customerEmail: String,
      status: PaymentStatus
  )

  case class PayoutRequest(
      recipientPhone: String, // e.g., +237670000000
      amount: BigDecimal,
      description: String
  )

  case class PayoutResult(
      reference: String,
      status: String,
      taxDeducted: BigDecimal
  )

  case class CameroonTaxAuthorityInvoiceReport(
      invoiceNumber: String,
      dateTime: OffsetDateTime,
      merchantId: String, // Your Cameroon Tax ID (NIU)
      customerName: String,
      grossAmount: BigDecimal,
      vatAmount: BigDecimal, // Standard 19.25% if applicable
      sepTaxAmount: BigDecimal, // The 3% Digital Tax for 2026
      netAmount: BigDecimal,
      paymentMethod: String, // e.g., "MOBILE_MONEY", "GIMAC_TRANSFER"
      transactionRef: String // Reference from MTN/Orange/GIMAC
  )

  object CameroonTaxAuthorityInvoiceReport {
    given JsonEncoder[CameroonTaxAuthorityInvoiceReport] =
      DeriveJsonEncoder.gen[CameroonTaxAuthorityInvoiceReport]
  }

  case class DirectorateGeneralOfTaxationInvoiceReport(
      invoiceNumber: String,
      dateTime: OffsetDateTime,
      merchantId: String, // The NIU (Taxpayer ID)
      customerName: String,
      grossAmount: BigDecimal,
      vatAmount: BigDecimal, // 19.25% (Standard) or 10% (Reduced)
      sepTaxAmount: BigDecimal, // 3% Digital Tax for 2026
      netAmount: BigDecimal, // Amount after tax deductions
      paymentMethod: String, // MOBILE_MONEY | GIMAC | CARD
      transactionRef: String // Aggregator/Bank reference
  )

  object DirectorateGeneralOfTaxationInvoiceReport {
    given JsonCodec[DirectorateGeneralOfTaxationInvoiceReport] =
      DeriveJsonCodec.gen[DirectorateGeneralOfTaxationInvoiceReport]
  }

  opaque type MemberId = String
  object MemberId {
    def apply(s: String): MemberId = s
    extension (id: MemberId) def value: String = id
    given JsonEncoder[MemberId] =
      JsonEncoder.string.contramap[MemberId](_.value)
    given JsonDecoder[MemberId] = JsonDecoder.string
  }

  opaque type TenantId = String
  object TenantId {
    def apply(s: String): TenantId = s
    extension (id: TenantId) def value: String = id
    given JsonEncoder[TenantId] =
      JsonEncoder.string.contramap[TenantId](_.value)
    given JsonDecoder[TenantId] = JsonDecoder.string
  }

  opaque type UserId = String
  object UserId {
    def apply(s: String): UserId = s
    extension (id: UserId) def value: String = id
    given JsonEncoder[UserId] = JsonEncoder.string.contramap[UserId](_.value)
    given JsonDecoder[UserId] = JsonDecoder.string
  }

  opaque type PaymentId = String
  object PaymentId {
    def apply(s: String): PaymentId = s
    extension (id: PaymentId) def value: String = id
    given JsonEncoder[PaymentId] =
      JsonEncoder.string.contramap[PaymentId](_.value)
    given JsonDecoder[PaymentId] = JsonDecoder.string
  }

  opaque type Money = BigDecimal // scala bigdecimal
  object Money {
    def apply(amount: BigDecimal): Money = amount
    extension (m: Money) def value: BigDecimal = m
    given JsonEncoder[Money] =
      JsonEncoder.scalaBigDecimal.contramap[Money](_.value)
    given JsonDecoder[Money] = JsonDecoder.bigDecimal.map(Money(_))
  }

  enum PaymentRail derives JsonCodec {
    case MTN_MOMO
    case ORANGE_MONEY
  }
  enum PaymentStatus2 derives JsonCodec {
    case PENDING
    case SUBMITTED
    case FAILED
  }
  final case class CreatePaymentRequest(
      idempotencyKey: String,
      amount: Money,
      rail: PaymentRail,
      msisdn: String
  ) derives JsonCodec

  final case class Payment(
      tenantId: String,
      paymentId: String,
      userId: String,
      amount: Money,
      rail: PaymentRail,
      msisdn: String,
      status: PaymentStatus2
  ) derives JsonCodec
}
