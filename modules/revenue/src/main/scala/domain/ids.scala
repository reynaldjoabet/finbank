package revenue.domain

import scala.annotation.targetName
import zio.json.*

object ids {

  opaque type TaxpayerId = String
  object TaxpayerId {
    def apply(s: String): TaxpayerId = s
    def unapply(id: TaxpayerId): String = id

    given CanEqual[TaxpayerId, TaxpayerId] = CanEqual.derived
    given JsonEncoder[TaxpayerId] = JsonEncoder.string
    given JsonDecoder[TaxpayerId] = JsonDecoder.string
  }
  extension (id: TaxpayerId) {
    @targetName("taxpayerIdValue") def value: String = id
  }

  opaque type ReturnId = String
  object ReturnId {
    def apply(s: String): ReturnId = s
    def unapply(id: ReturnId): String = id

    given CanEqual[ReturnId, ReturnId] = CanEqual.derived
    given JsonEncoder[ReturnId] = JsonEncoder.string
    given JsonDecoder[ReturnId] = JsonDecoder.string
  }
  extension (id: ReturnId) @targetName("returnIdValue") def value: String = id

  opaque type AssessmentId = String
  object AssessmentId {
    def apply(s: String): AssessmentId = s
    def unapply(id: AssessmentId): String = id

    given CanEqual[AssessmentId, AssessmentId] = CanEqual.derived
    given JsonEncoder[AssessmentId] = JsonEncoder.string
    given JsonDecoder[AssessmentId] = JsonDecoder.string
  }
  extension (id: AssessmentId) {
    @targetName("assessmentIdValue") def value: String = id
  }

  opaque type LiabilityId = String
  object LiabilityId {
    def apply(s: String): LiabilityId = s
    def unapply(id: LiabilityId): String = id

    given CanEqual[LiabilityId, LiabilityId] = CanEqual.derived
    given JsonEncoder[LiabilityId] = JsonEncoder.string
    given JsonDecoder[LiabilityId] = JsonDecoder.string
  }
  extension (id: LiabilityId) {
    @targetName("liabilityIdValue") def value: String = id
  }

  opaque type PaymentId = String
  object PaymentId {
    def apply(s: String): PaymentId = s
    def unapply(id: PaymentId): String = id

    given CanEqual[PaymentId, PaymentId] = CanEqual.derived
    given JsonEncoder[PaymentId] = JsonEncoder.string
    given JsonDecoder[PaymentId] = JsonDecoder.string
  }
  extension (id: PaymentId) @targetName("paymentIdValue") def value: String = id

  opaque type ReceiptId = String
  object ReceiptId {
    def apply(s: String): ReceiptId = s
    def unapply(id: ReceiptId): String = id

    given CanEqual[ReceiptId, ReceiptId] = CanEqual.derived
    given JsonEncoder[ReceiptId] = JsonEncoder.string
    given JsonDecoder[ReceiptId] = JsonDecoder.string
  }
  extension (id: ReceiptId) @targetName("receiptIdValue") def value: String = id

  opaque type RefundId = String
  object RefundId {
    def apply(s: String): RefundId = s
    def unapply(id: RefundId): String = id

    given CanEqual[RefundId, RefundId] = CanEqual.derived
    given JsonEncoder[RefundId] = JsonEncoder.string
    given JsonDecoder[RefundId] = JsonDecoder.string
  }
  extension (id: RefundId) @targetName("refundIdValue") def value: String = id

  opaque type ObjectionId = String
  object ObjectionId {
    def apply(s: String): ObjectionId = s
    def unapply(id: ObjectionId): String = id

    given CanEqual[ObjectionId, ObjectionId] = CanEqual.derived
    given JsonEncoder[ObjectionId] = JsonEncoder.string
    given JsonDecoder[ObjectionId] = JsonDecoder.string
  }
  extension (id: ObjectionId) {
    @targetName("objectionIdValue") def value: String = id
  }

  opaque type CaseId = String
  object CaseId {
    def apply(s: String): CaseId = s
    def unapply(id: CaseId): String = id

    given CanEqual[CaseId, CaseId] = CanEqual.derived
    given JsonEncoder[CaseId] = JsonEncoder.string
    given JsonDecoder[CaseId] = JsonDecoder.string
  }
  extension (id: CaseId) @targetName("caseIdValue") def value: String = id

  opaque type DocumentId = String
  object DocumentId {
    def apply(s: String): DocumentId = s
    def unapply(id: DocumentId): String = id

    given CanEqual[DocumentId, DocumentId] = CanEqual.derived
    given JsonEncoder[DocumentId] = JsonEncoder.string
    given JsonDecoder[DocumentId] = JsonDecoder.string
  }
  extension (id: DocumentId) {
    @targetName("documentIdValue") def value: String = id
  }

  opaque type RiskRuleId = String
  object RiskRuleId {
    def apply(s: String): RiskRuleId = s
    def unapply(id: RiskRuleId): String = id

    given CanEqual[RiskRuleId, RiskRuleId] = CanEqual.derived
    given JsonEncoder[RiskRuleId] = JsonEncoder.string
    given JsonDecoder[RiskRuleId] = JsonDecoder.string
  }
  extension (id: RiskRuleId) {
    @targetName("riskRuleIdValue") def value: String = id
  }

  opaque type UserId = String
  object UserId {
    def apply(s: String): UserId = s
    def unapply(id: UserId): String = id

    given CanEqual[UserId, UserId] = CanEqual.derived
    given JsonEncoder[UserId] = JsonEncoder.string
    given JsonDecoder[UserId] = JsonDecoder.string
  }
  extension (id: UserId) @targetName("userIdValue") def value: String = id

  opaque type RefreshTokenId = String
  object RefreshTokenId {
    def apply(s: String): RefreshTokenId = s
    def unapply(id: RefreshTokenId): String = id

    given CanEqual[RefreshTokenId, RefreshTokenId] = CanEqual.derived
    given JsonEncoder[RefreshTokenId] = JsonEncoder.string
    given JsonDecoder[RefreshTokenId] = JsonDecoder.string
  }
  extension (id: RefreshTokenId) {
    @targetName("refreshTokenIdValue") def value: String = id
  }
}
