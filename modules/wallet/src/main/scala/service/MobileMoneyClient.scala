package service

import domain.*
import zio.*

final case class InitiatePaymentRequest(
    amount: Money,
    customerMsisdn: String,
    narrative: String,
    callbackUrl: String,
    merchantReference: String
)

final case class InitiatePaymentResponse(
    externalRef: String,
    rawStatus: String
)

final case class ProviderWebhook(
    externalRef: String,
    rawStatus: String,
    amount: Money,
    customerMsisdn: String,
    occurredAtEpochSeconds: Long
)

trait MobileMoneyClient {
  def provider: Provider

  def initiatePayment(
      req: InitiatePaymentRequest
  ): IO[AppError, InitiatePaymentResponse]

  /** Optional but useful for reconciliation backfills. */
  def queryStatus(externalRef: String): IO[AppError, String]

  /** Verify webhook authenticity (HMAC/signature header, etc). */
  def verifyWebhook(
      headers: Map[String, String],
      rawBody: String
  ): IO[AppError, Unit]

  def parseWebhook(rawBody: String): IO[AppError, ProviderWebhook]
}
