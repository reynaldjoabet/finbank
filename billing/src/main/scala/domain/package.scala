import java.util.UUID
import zio.*
import zio.json.*
import java.time.OffsetDateTime
package object domain {

  opaque type InvoiceId = UUID
  object InvoiceId {
    def apply(uuid: UUID): InvoiceId = uuid
    def unapply(id: InvoiceId): UUID = id
    def random: UIO[InvoiceId] = ZIO.succeed(UUID.randomUUID())

    given CanEqual[InvoiceId, InvoiceId] = CanEqual.derived
  }

  enum PaymentStatus derives CanEqual {
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
    def unapply(id: MemberId): String = id

    given CanEqual[MemberId, MemberId] = CanEqual.derived
    given JsonEncoder[MemberId] = JsonEncoder.string
    given JsonDecoder[MemberId] = JsonDecoder.string
  }

  opaque type TenantId = String
  object TenantId {
    def apply(s: String): TenantId = s
    def unapply(id: TenantId): String = id

    given CanEqual[TenantId, TenantId] = CanEqual.derived
    given JsonEncoder[TenantId] = JsonEncoder.string
    given JsonDecoder[TenantId] = JsonDecoder.string
  }

  opaque type UserId = String
  object UserId {
    def apply(s: String): UserId = s
    def unapply(id: UserId): String = id

    given CanEqual[UserId, UserId] = CanEqual.derived
    given JsonEncoder[UserId] = JsonEncoder.string
    given JsonDecoder[UserId] = JsonDecoder.string
  }

  opaque type PaymentId = String
  object PaymentId {
    def apply(s: String): PaymentId = s
    def unapply(id: PaymentId): String = id

    given CanEqual[PaymentId, PaymentId] = CanEqual.derived
    given JsonEncoder[PaymentId] = JsonEncoder.string
    given JsonDecoder[PaymentId] = JsonDecoder.string
  }

  opaque type Money = BigDecimal // scala bigdecimal
  object Money {
    def apply(amount: BigDecimal): Money = amount
    def unapply(m: Money): BigDecimal = m

    given CanEqual[Money, Money] = CanEqual.derived
    given JsonEncoder[Money] = JsonEncoder.scalaBigDecimal
    given JsonDecoder[Money] = JsonDecoder.scalaBigDecimal
  }

  enum PaymentRail derives JsonCodec, CanEqual {
    case MTN_MOMO
    case ORANGE_MONEY
  }
  enum PaymentStatus2 derives JsonCodec, CanEqual {
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
