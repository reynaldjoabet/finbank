package service

import domain.*
import repo.*
import zio.*
import java.time.Instant

trait PaymentService {
  def createInvoice(
      merchantId: MerchantId,
      reference: String,
      customerMsisdn: String,
      total: Money
  ): IO[AppError, Invoice]

  def requestPayment(
      merchantId: MerchantId,
      invoiceId: InvoiceId,
      provider: Provider,
      idempotencyKey: String,
      callbackUrl: String
  ): IO[AppError, PaymentIntent]

  /** Webhook entrypoint (idempotent, signature verified, ledger-safe). */
  def handleWebhook(
      provider: Provider,
      headers: Map[String, String],
      rawBody: String
  ): IO[AppError, Unit]
}

final case class PaymentServiceLive(
    invoiceRepo: InvoiceRepo,
    paymentRepo: PaymentRepo,
    ledgerRepo: LedgerRepo,
    clients: Map[Provider, MobileMoneyClient]
) extends PaymentService {

  private def client(p: Provider): IO[AppError, MobileMoneyClient] = {
    ZIO
      .fromOption(clients.get(p))
      .orElseFail(
        AppError.ProviderError(s"No client configured for provider=$p")
      )
  }

  override def createInvoice(
      merchantId: MerchantId,
      reference: String,
      customerMsisdn: String,
      total: Money
  ): IO[AppError, Invoice] = {
    if (total.amountMinor <= 0) {
      ZIO.fail(AppError.Validation("Invoice total must be > 0"))
    } else {
      val invoice = Invoice(
        id = InvoiceId.random,
        merchantId = merchantId,
        reference = reference,
        customerMsisdn = customerMsisdn,
        total = total,
        paid = Money(0L, total.currency),
        status = InvoiceStatus.Unpaid,
        createdAt = Instant.now()
      )
      invoiceRepo.create(invoice).as(invoice)
    }
  }

  override def requestPayment(
      merchantId: MerchantId,
      invoiceId: InvoiceId,
      provider: Provider,
      idempotencyKey: String,
      callbackUrl: String
  ): IO[AppError, PaymentIntent] = {
    for {
      existing <- paymentRepo.getByIdempotency(merchantId, idempotencyKey)
      intent <- existing match {
        case Some(already) =>
          ZIO.succeed(already)

        case None =>
          for {
            invoice <- invoiceRepo.get(invoiceId)
            _ <- ZIO
              .fail(AppError.Conflict("Invoice already paid"))
              .when(invoice.status == InvoiceStatus.Paid)

            pid = PaymentId.random
            initial = PaymentIntent(
              id = pid,
              invoiceId = invoiceId,
              merchantId = merchantId,
              provider = provider,
              amount = Money(
                invoice.total.amountMinor - invoice.paid.amountMinor,
                invoice.total.currency
              ),
              externalRef = None,
              status = PaymentStatus.Initiated,
              idempotencyKey = idempotencyKey,
              createdAt = Instant.now()
            )

            _ <- paymentRepo.create(initial)

            c <- client(provider)
            resp <- c.initiatePayment(
              InitiatePaymentRequest(
                amount = initial.amount,
                customerMsisdn = invoice.customerMsisdn,
                narrative = s"Invoice ${invoice.reference}",
                callbackUrl = callbackUrl,
                merchantReference = initial.id.toString
              )
            )

            updated = initial.copy(
              externalRef = Some(resp.externalRef),
              status = PaymentStatus.PendingProvider
            )
            _ <- paymentRepo.update(updated)
          } yield updated
      }
    } yield intent
  }

  override def handleWebhook(
      provider: Provider,
      headers: Map[String, String],
      rawBody: String
  ): IO[AppError, Unit] = {
    for {
      c <- client(provider)
      _ <- c.verifyWebhook(headers, rawBody)
      wh <- c.parseWebhook(rawBody)

      // Prevent duplicate processing
      existing <- paymentRepo.getByExternalRef(provider, wh.externalRef)

      _ <- existing match {
        case None =>
          ZIO.fail(
            AppError.NotFound(
              s"No payment intent found for externalRef=${wh.externalRef}"
            )
          )

        case Some(intent) if intent.status == PaymentStatus.Succeeded =>
          ZIO.unit // idempotent: already processed successfully

        case Some(intent) =>
          val succeeded = wh.rawStatus.equalsIgnoreCase(
            "SUCCESS"
          ) || wh.rawStatus.equalsIgnoreCase("SUCCEEDED")

          if (!succeeded) {
            val failed = intent.copy(status = PaymentStatus.Failed)
            paymentRepo.update(failed).unit
          } else {
            for {
              invoice <- invoiceRepo.get(intent.invoiceId)

              // Update invoice paid amount; clamp to total
              newPaidMinor = Math.min(
                invoice.total.amountMinor,
                invoice.paid.amountMinor + wh.amount.amountMinor
              )
              newStatus =
                if (newPaidMinor >= invoice.total.amountMinor)
                  InvoiceStatus.Paid
                else InvoiceStatus.PartiallyPaid

              updatedInvoice = invoice.copy(
                paid = Money(newPaidMinor, invoice.total.currency),
                status = newStatus
              )

              _ <- invoiceRepo.update(updatedInvoice)

              // Ledger entry: credit merchant
              _ <- ledgerRepo.append(
                LedgerEntry(
                  merchantId = intent.merchantId,
                  invoiceId = intent.invoiceId,
                  paymentId = intent.id,
                  entryType = LedgerEntryType.Credit,
                  amount = wh.amount,
                  createdAt = Instant.now()
                )
              )

              _ <- paymentRepo.update(
                intent.copy(status = PaymentStatus.Succeeded)
              )
            } yield ()
          }
      }
    } yield ()
  }
}

object PaymentServiceLive {
  val layer: ZLayer[
    InvoiceRepo & PaymentRepo & LedgerRepo & Set[MobileMoneyClient],
    Nothing,
    PaymentService
  ] = {
    ZLayer.fromZIO {
      for {
        invoiceRepo <- ZIO.service[InvoiceRepo]
        paymentRepo <- ZIO.service[PaymentRepo]
        ledgerRepo <- ZIO.service[LedgerRepo]
        setClients <- ZIO.service[Set[MobileMoneyClient]]
        clients = setClients.map(c => c.provider -> c).toMap
      } yield PaymentServiceLive(invoiceRepo, paymentRepo, ledgerRepo, clients)
    }
  }
}
