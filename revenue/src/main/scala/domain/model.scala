package revenue.domain

import zio.json.*
import revenue.domain.ids.*

enum Role derives JsonCodec {
  case Taxpayer, Agent, Officer, Admin
}

final case class Principal(
    subject: String,
    roles: Set[Role],
    actingTaxpayerId: Option[TaxpayerId]
) derives JsonCodec

enum TaxpayerKind derives JsonCodec { case Individual, Business }

enum TaxType derives JsonCodec {
  case IncomeTax, VAT, Payroll_PAYE_CSG_NSF, CustomsDuty, Excise, GamblingTax,
    Levy
}

final case class Period(year: Int, month: Option[Int]) derives JsonCodec

final case class TaxpayerIdentifiers(
    nationalId: Option[String],
    brn: Option[String],
    ern: Option[String]
) derives JsonCodec

final case class Taxpayer(
    id: TaxpayerId,
    tan: String,
    kind: TaxpayerKind,
    legalName: String,
    identifiers: TaxpayerIdentifiers,
    status: String,
    createdAtEpochMs: Long
) derives JsonCodec

final case class TaxpayerRegistration(
    kind: TaxpayerKind,
    legalName: String,
    identifiers: TaxpayerIdentifiers
) derives JsonCodec

// Returns lifecycle (draft -> validated -> submitted -> amended)
enum ReturnStatus derives JsonCodec {
  case Draft, Validated, Submitted, Assessed, Rejected
}

final case class ReturnDraftCreate(
    taxpayerId: TaxpayerId,
    taxType: TaxType,
    period: Period
) derives JsonCodec

final case class ReturnDraftUpdate(
    payload: zio.json.ast.Json
) derives JsonCodec

final case class ValidationResult(
    ok: Boolean,
    errors: List[String]
) derives JsonCodec

final case class TaxReturn(
    id: ReturnId,
    taxpayerId: TaxpayerId,
    taxType: TaxType,
    period: Period,
    payload: zio.json.ast.Json,
    status: ReturnStatus,
    version: Int,
    amendedFrom: Option[ReturnId],
    createdAtEpochMs: Long,
    updatedAtEpochMs: Long,
    submittedAtEpochMs: Option[Long]
) derives JsonCodec

// Assessment & liabilities
enum LiabilityStatus derives JsonCodec { case Open, Paid, Cancelled }

final case class AssessmentCreate(
    returnId: ReturnId
) derives JsonCodec

final case class Assessment(
    id: AssessmentId,
    returnId: ReturnId,
    taxpayerId: TaxpayerId,
    taxType: TaxType,
    assessedAmount: BigDecimal,
    penalty: BigDecimal,
    interest: BigDecimal,
    currency: String,
    createdAtEpochMs: Long
) derives JsonCodec

final case class Liability(
    id: LiabilityId,
    taxpayerId: TaxpayerId,
    assessmentId: AssessmentId,
    taxType: TaxType,
    amount: BigDecimal,
    currency: String,
    dueDateEpochMs: Long,
    status: LiabilityStatus
) derives JsonCodec

final case class LiabilityRecalcResult(
    liabilityId: LiabilityId,
    oldAmount: BigDecimal,
    newAmount: BigDecimal,
    penaltyAdded: BigDecimal,
    interestAdded: BigDecimal
) derives JsonCodec

// Payments
enum PaymentMethod derives JsonCodec { case DirectDebit, Card, BankTransfer }
enum PaymentStatus derives JsonCodec { case Pending, Settled, Failed }

final case class PaymentIntentCreate(
    taxpayerId: TaxpayerId,
    liabilityId: LiabilityId,
    method: PaymentMethod,
    amount: Option[BigDecimal],
    currency: Option[String]
) derives JsonCodec

final case class Payment(
    id: PaymentId,
    taxpayerId: TaxpayerId,
    liabilityId: LiabilityId,
    method: PaymentMethod,
    amount: BigDecimal,
    currency: String,
    status: PaymentStatus,
    createdAtEpochMs: Long,
    settledAtEpochMs: Option[Long]
) derives JsonCodec

final case class PaymentConfirm(
    // gateway reference or payload would go here
    gatewayRef: Option[String]
) derives JsonCodec

final case class Receipt(
    id: ReceiptId,
    paymentId: PaymentId,
    issuedAtEpochMs: Long,
    reference: String
) derives JsonCodec

// Refunds
enum RefundStatus derives JsonCodec {
  case Draft, Submitted, Approved, Rejected, Paid
}

final case class RefundClaimCreate(
    taxpayerId: TaxpayerId,
    taxType: TaxType,
    period: Period,
    amount: BigDecimal,
    currency: String,
    reason: String
) derives JsonCodec

final case class RefundClaim(
    id: RefundId,
    taxpayerId: TaxpayerId,
    taxType: TaxType,
    period: Period,
    amount: BigDecimal,
    currency: String,
    reason: String,
    status: RefundStatus,
    createdAtEpochMs: Long,
    updatedAtEpochMs: Long
) derives JsonCodec

final case class RefundDecision(
    reason: Option[String]
) derives JsonCodec

// Objections
enum ObjectionStatus derives JsonCodec { case Submitted, Withdrawn, Resolved }

final case class ObjectionCreate(
    taxpayerId: TaxpayerId,
    referenceType: String, // "assessment" | "refund" | ...
    referenceId: String,
    grounds: String
) derives JsonCodec

final case class Objection(
    id: ObjectionId,
    taxpayerId: TaxpayerId,
    referenceType: String,
    referenceId: String,
    grounds: String,
    status: ObjectionStatus,
    createdAtEpochMs: Long,
    updatedAtEpochMs: Long
) derives JsonCodec

// Audit trail
final case class AuditEvent(
    atEpochMs: Long,
    principal: Principal,
    action: String,
    entityType: String,
    entityId: String,
    details: String
) derives JsonCodec

final case class RiskRule(
    id: RiskRuleId,
    name: String,
    enabled: Boolean,
    taxTypes: Option[List[TaxType]],
    jsonField: String, // e.g. "declaredAmount"
    threshold: BigDecimal, // e.g. 5000000
    caseType: CaseType,
    caseReason: String,
    createdAtEpochMs: Long,
    updatedAtEpochMs: Long
) derives JsonCodec

final case class RiskRuleCreate(
    name: String,
    taxTypes: Option[List[TaxType]],
    jsonField: String,
    threshold: BigDecimal,
    caseType: CaseType,
    caseReason: String
) derives JsonCodec
