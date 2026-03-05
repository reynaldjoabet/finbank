package com.migrantbank.api

import com.migrantbank.domain.*
import zio.json.*
import java.util.UUID

final case class StartRegistrationRequest(
    firstName: String,
    lastName: String,
    dateOfBirth: String,
    phone: String,
    address: String,
    ssn: String
) derives JsonEncoder,
      JsonDecoder {
  def toProfile: UserProfile =
    UserProfile(firstName, lastName, dateOfBirth, phone, address, ssn)
}

final case class StartRegistrationResponse(userId: UUID, kycStatus: KycStatus)
    derives JsonEncoder,
      JsonDecoder

final case class ConfirmRegistrationRequest(
    userId: UUID,
    smsCode: String,
    password: String
) derives JsonEncoder,
      JsonDecoder

final case class LoginRequest(phone: String, password: String)
    derives JsonEncoder,
      JsonDecoder
final case class RefreshRequest(userId: UUID, refreshToken: String)
    derives JsonEncoder,
      JsonDecoder
final case class LogoutRequest(userId: UUID, refreshToken: String)
    derives JsonEncoder,
      JsonDecoder

final case class TopUpRequest(
    amountMinor: Long,
    currency: String,
    source: String
) derives JsonEncoder,
      JsonDecoder
final case class CashDepositRequest(
    amountMinor: Long,
    currency: String,
    branchRef: String
) derives JsonEncoder,
      JsonDecoder

final case class P2PTransferRequest(
    toUserId: UUID,
    amountMinor: Long,
    currency: String,
    note: Option[String]
) derives JsonEncoder,
      JsonDecoder
final case class AchTransferRequest(
    destination: String,
    amountMinor: Long,
    currency: String,
    note: Option[String]
) derives JsonEncoder,
      JsonDecoder

final case class CreateFamilyGroupRequest(memberUserIds: Set[UUID])
    derives JsonEncoder,
      JsonDecoder
final case class FamilyDistributeRequest(
    groupId: UUID,
    payouts: Map[UUID, Money]
) derives JsonEncoder,
      JsonDecoder

final case class EnrollPaycheckRequest(employerName: String)
    derives JsonEncoder,
      JsonDecoder

final case class LoanRequest(amountMinor: Long, currency: String)
    derives JsonEncoder,
      JsonDecoder

final case class CreateTicketRequest(message: String)
    derives JsonEncoder,
      JsonDecoder

final case class AdminSetKycRequest(status: KycStatus)
    derives JsonEncoder,
      JsonDecoder
final case class AdminUpdateDeliveryRequest(status: DeliveryStatus)
    derives JsonEncoder,
      JsonDecoder
final case class AdminTransferStatusRequest(status: TransferStatus)
    derives JsonEncoder,
      JsonDecoder
