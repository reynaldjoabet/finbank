package com.migrantbank.domain

import zio.json.*
import java.time.Instant
import java.util.UUID
import com.augustnagro.magnum.DbCodec

given CanEqual[UUID, UUID] = CanEqual.derived

final case class Money(amountMinor: Long, currency: String)
    derives JsonEncoder,
      JsonDecoder

enum KycStatus derives JsonEncoder, JsonDecoder, DbCodec, CanEqual {
  case PENDING, VERIFIED, REJECTED, MANUAL_REVIEW_REQUIRED
}
enum CardKind derives JsonEncoder, JsonDecoder, CanEqual {
  case VIRTUAL, PHYSICAL
}
enum CardStatus derives JsonEncoder, JsonDecoder, CanEqual {
  case ACTIVE, BLOCKED, CLOSED
}
enum DeliveryStatus derives JsonEncoder, JsonDecoder, CanEqual {
  case NOT_ORDERED, ORDERED, SHIPPED, DELIVERED, FAILED
}
enum TransferType derives JsonEncoder, JsonDecoder, CanEqual {
  case P2P, ACH
}
enum TransferStatus derives JsonEncoder, JsonDecoder, CanEqual {
  case PROCESSING, COMPLETED, FAILED
}
enum LoanStatus derives JsonEncoder, JsonDecoder, CanEqual {
  case OFFERED, ACTIVE, REPAID, DEFAULTED
}
enum TicketStatus derives JsonEncoder, JsonDecoder, CanEqual {
  case OPEN, IN_PROGRESS, CLOSED
}
final case class UserProfile(
    firstName: String,
    lastName: String,
    dateOfBirth: String,
    phone: String,
    address: String,
    ssn: String
) derives JsonEncoder,
      JsonDecoder

final case class User(
    id: UUID,
    profile: UserProfile,
    kycStatus: KycStatus,
    role: String,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class Account(
    id: UUID,
    userId: Option[UUID],
    accountType: String,
    name: String,
    currency: String,
    balanceMinor: Long,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class Card(
    id: UUID,
    userId: UUID,
    kind: CardKind,
    last4: String,
    status: CardStatus,
    deliveryStatus: DeliveryStatus,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class Transfer(
    id: UUID,
    transferType: TransferType,
    fromUserId: UUID,
    toUserId: Option[UUID],
    achDestination: Option[String],
    amount: Money,
    note: Option[String],
    status: TransferStatus,
    idempotencyKey: Option[String],
    riskFlag: Boolean,
    riskReason: Option[String],
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class FamilyGroup(
    id: UUID,
    ownerUserId: UUID,
    memberUserIds: Set[UUID],
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class PaycheckEnrollment(
    userId: UUID,
    employerName: String,
    partnerRef: String,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class Loan(
    id: UUID,
    userId: UUID,
    principal: Money,
    feeMinor: Long,
    dueDateIso: String,
    status: LoanStatus,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class SupportTicket(
    id: UUID,
    userId: UUID,
    message: String,
    status: TicketStatus,
    createdAt: Instant
) derives JsonEncoder,
      JsonDecoder

final case class AuditEvent(
    at: Instant,
    kind: String,
    userId: Option[UUID],
    correlationId: String,
    details: String
) derives JsonEncoder,
      JsonDecoder

final case class AuthTokens(accessToken: String, refreshToken: String)
    derives JsonEncoder,
      JsonDecoder

final case class AuthContext(userId: UUID, role: String)
