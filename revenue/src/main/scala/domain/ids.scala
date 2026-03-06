package revenue.domain

import zio.json.*

object ids {

  final case class TaxpayerId(value: String) extends AnyVal
  object TaxpayerId {
    given JsonCodec[TaxpayerId] =
      JsonCodec[String].transform(TaxpayerId(_), _.value)
  }

  final case class ReturnId(value: String) extends AnyVal
  object ReturnId {
    given JsonCodec[ReturnId] =
      JsonCodec[String].transform(ReturnId(_), _.value)
  }

  final case class AssessmentId(value: String) extends AnyVal
  object AssessmentId {
    given JsonCodec[AssessmentId] =
      JsonCodec[String].transform(AssessmentId(_), _.value)
  }

  final case class LiabilityId(value: String) extends AnyVal
  object LiabilityId {
    given JsonCodec[LiabilityId] =
      JsonCodec[String].transform(LiabilityId(_), _.value)
  }

  final case class PaymentId(value: String) extends AnyVal
  object PaymentId {
    given JsonCodec[PaymentId] =
      JsonCodec[String].transform(PaymentId(_), _.value)
  }

  final case class ReceiptId(value: String) extends AnyVal
  object ReceiptId {
    given JsonCodec[ReceiptId] =
      JsonCodec[String].transform(ReceiptId(_), _.value)
  }

  final case class RefundId(value: String) extends AnyVal
  object RefundId {
    given JsonCodec[RefundId] =
      JsonCodec[String].transform(RefundId(_), _.value)
  }

  final case class ObjectionId(value: String) extends AnyVal
  object ObjectionId {
    given JsonCodec[ObjectionId] =
      JsonCodec[String].transform(ObjectionId(_), _.value)
  }

  final case class CaseId(value: String) extends AnyVal
  object CaseId {
    given JsonCodec[CaseId] =
      JsonCodec[String].transform(CaseId(_), _.value)
  }

  final case class DocumentId(value: String) extends AnyVal
  object DocumentId {
    given JsonCodec[DocumentId] =
      JsonCodec[String].transform(DocumentId(_), _.value)
  }

  final case class RiskRuleId(value: String) extends AnyVal
  object RiskRuleId {
    given JsonCodec[RiskRuleId] =
      JsonCodec[String].transform(RiskRuleId(_), _.value)
  }

  final case class UserId(value: String) extends AnyVal
  object UserId {
    given JsonCodec[UserId] =
      JsonCodec[String].transform(UserId(_), _.value)
  }

  final case class RefreshTokenId(value: String) extends AnyVal
  object RefreshTokenId {
    given JsonCodec[RefreshTokenId] =
      JsonCodec[String].transform(RefreshTokenId(_), _.value)
  }
}