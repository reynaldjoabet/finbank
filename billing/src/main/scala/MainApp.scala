//package billing

import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*

import java.time.Instant

// =======================
// Domain (pure)
// =======================

enum InvoiceStatus derives CanEqual {
  case Draft, Issued, Paid, Voided
}

enum PaymentStatus derives CanEqual {
  case Pending, Failed, Settled
}

enum KybStatus derives CanEqual {
  case NotStarted, Submitted, Approved, Rejected
}

final case class Money(amount: BigDecimal, currency: String)

final case class Invoice(
    id: String,
    customerId: String,
    money: Money,
    status: InvoiceStatus,
    createdAt: Instant
)

final case class Payment(
    id: String,
    invoiceId: String,
    processorRef: Option[String],
    status: PaymentStatus,
    createdAt: Instant
)

final case class Business(
    id: String,
    legalName: String,
    kybStatus: KybStatus
)

sealed trait BillingError extends Throwable {
  def msg: String
  override def getMessage(): String = msg
}

object BillingError {
  final case class NotFound(entity: String, id: String) extends BillingError {
    val msg: String = s"$entity not found: $id"
  }

  final case class Validation(reason: String) extends BillingError {
    val msg: String = s"Validation: $reason"
  }

  final case class Conflict(reason: String) extends BillingError {
    val msg: String = s"Conflict: $reason"
  }

  final case class Integration(vendor: String, reason: String)
      extends BillingError {
    val msg: String = s"Integration[$vendor]: $reason"
  }
}

sealed trait BillingEvent derives JsonEncoder {
  def at: Instant
}
object BillingEvent {
  final case class InvoiceIssued(invoiceId: String, at: Instant)
      extends BillingEvent
  final case class PaymentInitiated(
      paymentId: String,
      invoiceId: String,
      at: Instant
  ) extends BillingEvent
  final case class PaymentSettled(
      paymentId: String,
      invoiceId: String,
      at: Instant
  ) extends BillingEvent
  final case class KybSubmitted(businessId: String, at: Instant)
      extends BillingEvent
}

// =======================
// Config
// =======================

final case class AppConfig(
    webhookDestinations: Chunk[String],
    vendorTimeout: Duration,
    reconciliationInterval: Duration
)

object AppConfig {
  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer.succeed(
      AppConfig(
        webhookDestinations =
          Chunk("https://example.com/webhook/billing-events"),
        vendorTimeout = 10.seconds,
        reconciliationInterval = 20.seconds
      )
    )
}

// =======================
// ZIO Modules (no "ports" terminology)
// Case classes of functions = light & testable
// =======================

final case class InvoiceStore(
    create: Invoice => IO[BillingError, Unit],
    get: String => IO[BillingError, Option[Invoice]],
    updateStatus: (String, InvoiceStatus) => IO[BillingError, Unit]
)

object InvoiceStore {
  val inMemory: ZLayer[Any, Nothing, InvoiceStore] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, Invoice]).map { ref =>
        InvoiceStore(
          create = (inv: Invoice) => ref.update(_.updated(inv.id, inv)).unit,
          get = (id: String) => ref.get.map(_.get(id)),
          updateStatus = (id: String, st: InvoiceStatus) =>
            ref.update { m =>
              m.get(id) match {
                case Some(inv) => m.updated(id, inv.copy(status = st))
                case None      => m
              }
            }.unit
        )
      }
    }
}

final case class PaymentStore(
    create: Payment => IO[BillingError, Unit],
    get: String => IO[BillingError, Option[Payment]],
    updateStatus: (String, PaymentStatus) => IO[BillingError, Unit],
    updateProcessorRef: (String, String) => IO[BillingError, Unit],
    listByStatus: PaymentStatus => IO[BillingError, List[Payment]]
)

object PaymentStore {
  val inMemory: ZLayer[Any, Nothing, PaymentStore] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, Payment]).map { ref =>
        PaymentStore(
          create = (p: Payment) => ref.update(_.updated(p.id, p)).unit,
          get = (id: String) => ref.get.map(_.get(id)),
          updateStatus = (id: String, st: PaymentStatus) =>
            ref.update { m =>
              m.get(id) match {
                case Some(p) => m.updated(id, p.copy(status = st))
                case None    => m
              }
            }.unit,
          updateProcessorRef = (id: String, refValue: String) =>
            ref.update { m =>
              m.get(id) match {
                case Some(p) =>
                  m.updated(id, p.copy(processorRef = Some(refValue)))
                case None => m
              }
            }.unit,
          listByStatus = (st: PaymentStatus) =>
            ref.get.map(_.values.filter(_.status == st).toList)
        )
      }
    }
}

final case class BusinessStore(
    upsert: Business => IO[BillingError, Unit],
    get: String => IO[BillingError, Option[Business]],
    updateKybStatus: (String, KybStatus) => IO[BillingError, Unit]
)

object BusinessStore {
  val inMemory: ZLayer[Any, Nothing, BusinessStore] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, Business]).map { ref =>
        BusinessStore(
          upsert = (b: Business) => ref.update(_.updated(b.id, b)).unit,
          get = (id: String) => ref.get.map(_.get(id)),
          updateKybStatus = (id: String, st: KybStatus) =>
            ref.update { m =>
              m.get(id) match {
                case Some(b) => m.updated(id, b.copy(kybStatus = st))
                case None    => m
              }
            }.unit
        )
      }
    }
}

/** Best-practice idempotency:
  *   - Store key -> paymentId mapping so retries return the same paymentId.
  *   - If vendor call fails, keep the mapping and mark payment failed. To retry
  *     safely, clients should use a NEW idempotency key.
  */
final case class IdempotencyStore(
    get: String => UIO[Option[String]],
    put: (String, String) => UIO[Unit]
)

object IdempotencyStore {
  val inMemory: ZLayer[Any, Nothing, IdempotencyStore] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, String]).map { ref =>
        IdempotencyStore(
          get = (k: String) => ref.get.map(_.get(k)),
          put = (k: String, v: String) => ref.update(_.updated(k, v)).unit
        )
      }
    }
}

/** Event pipeline with fan-out:
  *   - Business logic calls Events.emit(event) (fast, non-blocking)
  *   - A scoped publisher fiber moves events from an internal queue into a hub
  *     (multi-subscriber stream)
  */
final case class Events(
    emit: BillingEvent => UIO[Unit],
    stream: ZStream[Any, Nothing, BillingEvent]
)

object Events {
  val live: ZLayer[Any, Nothing, Events] =
    ZLayer.scoped {
      for {
        queue <- Queue.unbounded[BillingEvent]
        hub <- Hub.unbounded[BillingEvent]
        _ <- (queue.take
          .flatMap { ev =>
            hub.publish(ev).unit
          })
          .forever
          .forkScoped
      } yield Events(
        emit = (ev: BillingEvent) => queue.offer(ev).unit,
        stream = ZStream.fromHub(hub)
      )
    }
}

// =======================
// Integrations (stubs here, real HTTP later)
// =======================

final case class PaymentProcessor(
    chargeAch: (Invoice, String) => IO[BillingError, String],
    settlementStatus: String => IO[BillingError, PaymentStatus]
)

object PaymentProcessor {
  val stub: ZLayer[Any, Nothing, PaymentProcessor] =
    ZLayer.succeed {
      PaymentProcessor(
        chargeAch = (inv: Invoice, idemKey: String) =>
          ZIO.succeed(s"dwolla-ref-${inv.id}-${idemKey.take(10)}"),
        settlementStatus = (_: String) => ZIO.succeed(PaymentStatus.Settled)
      )
    }
}

final case class Kyb(
    submit: Business => IO[BillingError, Unit]
)

object Kyb {
  val stub: ZLayer[Any, Nothing, Kyb] =
    ZLayer.succeed {
      Kyb(
        submit = (b: Business) => ZIO.logInfo(s"[KYB] submitted ${b.id}").unit
      )
    }
}

final case class Accounting(
    upsertInvoice: Invoice => IO[BillingError, Unit],
    recordPayment: Payment => IO[BillingError, Unit]
)

object Accounting {
  val stub: ZLayer[Any, Nothing, Accounting] =
    ZLayer.succeed {
      Accounting(
        upsertInvoice = (inv: Invoice) =>
          ZIO.logInfo(s"[ACCT] upsert invoice ${inv.id}").unit,
        recordPayment =
          (pay: Payment) => ZIO.logInfo(s"[ACCT] record payment ${pay.id}").unit
      )
    }
}

final case class Email(
    sendInvoiceIssued: Invoice => IO[BillingError, Unit]
)

object Email {
  val stub: ZLayer[Any, Nothing, Email] =
    ZLayer.succeed {
      Email(
        sendInvoiceIssued = (inv: Invoice) =>
          ZIO.logInfo(s"[EMAIL] invoice issued ${inv.id}").unit
      )
    }
}

final case class Webhooks(
    postEvent: (String, String) => IO[BillingError, Unit]
)

object Webhooks {
  val stub: ZLayer[Any, Nothing, Webhooks] =
    ZLayer.succeed {
      Webhooks(
        postEvent = (url: String, payload: String) =>
          ZIO.logInfo(s"[WEBHOOK] POST $url payload=$payload").unit
      )
    }
}

// =======================
// Application Service (business workflows + best practices)
// =======================

final case class Billing(
    issueInvoice: (String, Money) => IO[BillingError, String],
    initiateAchPayment: (String, String) => IO[BillingError, String],
    submitKyb: (String, String) => IO[BillingError, Unit],
    getInvoice: String => IO[BillingError, Invoice],
    getPaymentStatus: String => IO[BillingError, PaymentStatus]
)

object Billing {

  private val vendorRetry: Schedule[Any, Any, (Duration, Long)] =
    (Schedule.exponential(200.millis) && Schedule.recurs(5)).jittered

  val live: ZLayer[
    InvoiceStore & PaymentStore & BusinessStore & IdempotencyStore &
      PaymentProcessor & Kyb & Email & Events & AppConfig,
    Nothing,
    Billing
  ] =
    ZLayer.fromFunction {
      (
          invoiceStore: InvoiceStore,
          paymentStore: PaymentStore,
          businessStore: BusinessStore,
          idem: IdempotencyStore,
          processor: PaymentProcessor,
          kyb: Kyb,
          email: Email,
          events: Events,
          cfg: AppConfig
      ) =>
        Billing(
          issueInvoice = (customerId: String, money: Money) =>
            ZIO.logSpan("issueInvoice") {
              for {
                now <- Clock.instant
                id <- Random.nextUUID.map(_.toString)
                inv = Invoice(id, customerId, money, InvoiceStatus.Issued, now)
                _ <- invoiceStore.create(inv)
                // best practice: side effects should not block the core transaction; treat as best-effort
                _ <- email
                  .sendInvoiceIssued(inv)
                  .timeoutFail(BillingError.Integration("email", "timeout"))(
                    cfg.vendorTimeout
                  )
                  .either
                  .unit
                _ <- events.emit(BillingEvent.InvoiceIssued(id, now))
              } yield id
            },

          initiateAchPayment = (invoiceId: String, idempotencyKey: String) =>
            ZIO.logSpan("initiateAchPayment") {
              for {
                existing <- idem.get(idempotencyKey)
                result <- existing match {
                  case Some(paymentId) =>
                    ZIO.succeed(paymentId)

                  case None =>
                    for {
                      invOpt <- invoiceStore.get(invoiceId)
                      inv <- ZIO
                        .fromOption(invOpt)
                        .orElseFail(BillingError.NotFound("Invoice", invoiceId))

                      _ <- ZIO
                        .fail(BillingError.Conflict("Invoice already paid"))
                        .when(inv.status == InvoiceStatus.Paid)

                      now <- Clock.instant
                      paymentId <- Random.nextUUID.map(_.toString)

                      // idempotency first: reserve key -> paymentId & persist a pending record
                      _ <- idem.put(idempotencyKey, paymentId)
                      _ <- paymentStore.create(
                        Payment(
                          id = paymentId,
                          invoiceId = invoiceId,
                          processorRef = None,
                          status = PaymentStatus.Pending,
                          createdAt = now
                        )
                      )

                      // vendor call: timeout + retry with backoff/jitter
                      ref <- processor
                        .chargeAch(inv, idempotencyKey)
                        .timeoutFail(
                          BillingError.Integration("payments", "timeout")
                        )(cfg.vendorTimeout)
                        .retry(vendorRetry)
                        .tapError(e =>
                          ZIO.logWarning(
                            s"Payment initiation failed: ${e.getMessage}"
                          )
                        )

                      _ <- paymentStore.updateProcessorRef(paymentId, ref)
                      _ <- events.emit(
                        BillingEvent.PaymentInitiated(paymentId, invoiceId, now)
                      )
                    } yield paymentId
                }
              } yield result
            },

          submitKyb = (businessId: String, legalName: String) =>
            ZIO.logSpan("submitKyb") {
              for {
                now <- Clock.instant
                b = Business(businessId, legalName, KybStatus.Submitted)
                _ <- businessStore.upsert(b)
                _ <- kyb
                  .submit(b)
                  .timeoutFail(BillingError.Integration("kyb", "timeout"))(
                    cfg.vendorTimeout
                  )
                  .retry(vendorRetry)
                _ <- events.emit(BillingEvent.KybSubmitted(businessId, now))
              } yield ()
            },

          getInvoice = (id: String) =>
            invoiceStore.get(id).flatMap { opt =>
              ZIO
                .fromOption(opt)
                .orElseFail(BillingError.NotFound("Invoice", id))
            },

          getPaymentStatus = (id: String) =>
            paymentStore
              .get(id)
              .flatMap { opt =>
                ZIO
                  .fromOption(opt)
                  .orElseFail(BillingError.NotFound("Payment", id))
              }
              .map(_.status)
        )
    }
}

// =======================
// Background workers (event-driven + reconciliation)
// =======================

object Workers {

  private val retry5: Schedule[Any, Any, (Duration, Long)] =
    (Schedule.exponential(200.millis) && Schedule.recurs(5)).jittered

  def accountingSync: ZIO[
    Events & Accounting & InvoiceStore & PaymentStore,
    Nothing,
    Unit
  ] = {
    ZStream
      .serviceWithStream[Events](_.stream)
      .mapZIO { ev =>
        (ev match {
          case BillingEvent.InvoiceIssued(invoiceId, _) =>
            for {
              invOpt <- ZIO.serviceWithZIO[InvoiceStore](_.get(invoiceId))
              inv <- ZIO
                .fromOption(invOpt)
                .orElseFail(BillingError.NotFound("Invoice", invoiceId))
              _ <- ZIO.serviceWithZIO[Accounting](_.upsertInvoice(inv))
            } yield ()

          case BillingEvent.PaymentSettled(paymentId, _, _) =>
            for {
              payOpt <- ZIO.serviceWithZIO[PaymentStore](_.get(paymentId))
              pay <- ZIO
                .fromOption(payOpt)
                .orElseFail(BillingError.NotFound("Payment", paymentId))
              _ <- ZIO.serviceWithZIO[Accounting](_.recordPayment(pay))
            } yield ()

          case _ =>
            ZIO.unit
        }).retry(retry5).catchAll { e =>
          ZIO.logWarning(s"[ACCT] failed: ${e.getMessage}")
        }
      }
      .runDrain
  }

  def webhookDispatch: ZIO[Events & Webhooks & AppConfig, Nothing, Unit] = {
    ZStream
      .serviceWithStream[Events](_.stream)
      .mapZIO { ev =>
        val payload = ev.toJson // small JSON encoder below
        for {
          cfg <- ZIO.service[AppConfig]
          _ <- ZIO.foreachDiscard(cfg.webhookDestinations) { url =>
            ZIO
              .serviceWithZIO[Webhooks](_.postEvent(url, payload))
              .retry(retry5)
              .catchAll(e =>
                ZIO.logWarning(s"[WEBHOOK] failed for $url: ${e.getMessage}")
              )
          }
        } yield ()
      }
      .runDrain
  }

  /** Reconciliation worker:
    *   - polls pending payments
    *   - checks settlement status with processor
    *   - updates payment + invoice
    *   - emits PaymentSettled event
    */
  def reconciliation = {
    val tick =
      for {
        cfg <- ZIO.service[AppConfig]
        pending <- ZIO.serviceWithZIO[PaymentStore](
          _.listByStatus(PaymentStatus.Pending)
        )
        _ <- ZIO.foreachDiscard(pending) { p =>
          p.processorRef match {
            case None =>
              ZIO.unit // not initiated yet (should be rare)
            case Some(ref) =>
              (for {
                st <- ZIO
                  .serviceWithZIO[PaymentProcessor](_.settlementStatus(ref))
                  .retry(retry5)
                _ <- st match {
                  case PaymentStatus.Settled =>
                    for {
                      now <- Clock.instant
                      _ <- ZIO.serviceWithZIO[PaymentStore](
                        _.updateStatus(p.id, PaymentStatus.Settled)
                      )
                      _ <- ZIO.serviceWithZIO[InvoiceStore](
                        _.updateStatus(p.invoiceId, InvoiceStatus.Paid)
                      )
                      _ <- ZIO.serviceWithZIO[Events](
                        _.emit(
                          BillingEvent.PaymentSettled(p.id, p.invoiceId, now)
                        )
                      )
                    } yield ()
                  case PaymentStatus.Failed =>
                    ZIO.serviceWithZIO[PaymentStore](
                      _.updateStatus(p.id, PaymentStatus.Failed)
                    )
                  case PaymentStatus.Pending =>
                    ZIO.unit
                }
              } yield ()).catchAll(e =>
                ZIO.logWarning(
                  s"[RECON] failed for payment=${p.id}: ${e.getMessage}"
                )
              )
          }
        }
      } yield ()

    tick.repeat(Schedule.spaced(20.seconds)).unit
  }
}

// =======================
// API (zio-http)
// =======================

object HttpApi {

  // Requests
  final case class IssueInvoiceReq(
      customerId: String,
      amount: BigDecimal,
      currency: String
  )
  object IssueInvoiceReq {
    given JsonCodec[IssueInvoiceReq] = DeriveJsonCodec.gen[IssueInvoiceReq]
  }

  final case class InitiateAchReq(idempotencyKey: String)
  object InitiateAchReq {
    given JsonCodec[InitiateAchReq] = DeriveJsonCodec.gen[InitiateAchReq]
  }

  // Basic auth guard (replace with JWT verification/OIDC in real systems)
  private def requireAuth(req: Request): IO[Response, Unit] = {
    val ok = req.headers
      .get(Header.Authorization)
      .exists(_.renderedValue.startsWith("Bearer "))
    if (ok) ZIO.unit else ZIO.fail(Response.status(Status.Unauthorized))
  }

  private def toHttpError(e: BillingError): Response = {
    e match {
      case BillingError.NotFound(_, _) =>
        Response.text(e.getMessage()).status(Status.NotFound)
      case BillingError.Validation(_) =>
        Response.text(e.getMessage()).status(Status.BadRequest)
      case BillingError.Conflict(_) =>
        Response.text(e.getMessage()).status(Status.Conflict)
      case BillingError.Integration(_, _) =>
        Response.text(e.getMessage()).status(Status.BadGateway)
    }
  }

  val routes =
    Routes(
      //   Method.POST / "api" / "invoices" -> handler { (req: Request) =>
      //     (for {
      //       _ <- requireAuth(req)
      //       body <- req.body.asString
      //       dto <- ZIO.fromEither(body.fromJson[IssueInvoiceReq].left.map(err => Response.text(err).status(Status.BadRequest)))
      //       id <- ZIO.serviceWithZIO[Billing](_.issueInvoice(dto.customerId, Money(dto.amount, dto.currency)))
      //         .mapError(toHttpError)
      //     } yield Response.json(s"""{"invoiceId":"$id"}""")).catchAll(ZIO.succeed(_))
      //   },

      Method.GET / "api" / "invoices" / string("id") -> handler {
        (id: String, req: Request) =>
          (for {
            _ <- requireAuth(req)
            inv <- ZIO
              .serviceWithZIO[Billing](_.getInvoice(id))
              .mapError(toHttpError)
          } yield Response.json(
            s"""{"id":"${inv.id}","customerId":"${inv.customerId}","amount":${inv.money.amount},"currency":"${inv.money.currency}","status":"${inv.status}"}"""
          )).catchAll(ZIO.succeed(_))
      },

      //   Method.POST / "api" / "invoices" / string("id") / "payments" / "ach" -> handler { (id: String, req: Request) =>
      //     (for {
      //       _ <- requireAuth(req)
      //       body <- req.body.asString
      //       dto <- ZIO.fromEither(body.fromJson[InitiateAchReq].left.map(err => Response.text(err).status(Status.BadRequest)))
      //       payId <- ZIO.serviceWithZIO[Billing](_.initiateAchPayment(id, dto.idempotencyKey)).mapError(toHttpError)
      //     } yield Response.json(s"""{"paymentId":"$payId"}""")).catchAll(ZIO.succeed(_))
      //   },

      Method.GET / "api" / "payments" / string("id") / "status" -> handler {
        (id: String, req: Request) =>
          (for {
            _ <- requireAuth(req)
            st <- ZIO
              .serviceWithZIO[Billing](_.getPaymentStatus(id))
              .mapError(toHttpError)
          } yield Response.json(s"""{"paymentId":"$id","status":"$st"}"""))
            .catchAll(ZIO.succeed(_))
      }
    )
}

// Small JSON encoder for events (good enough for webhooks demo)
extension (e: BillingEvent) {
  def toJson: String = {
    e match {
      case BillingEvent.InvoiceIssued(invoiceId, at) =>
        s"""{"type":"InvoiceIssued","invoiceId":"$invoiceId","at":"$at"}"""
      case BillingEvent.PaymentInitiated(paymentId, invoiceId, at) =>
        s"""{"type":"PaymentInitiated","paymentId":"$paymentId","invoiceId":"$invoiceId","at":"$at"}"""
      case BillingEvent.PaymentSettled(paymentId, invoiceId, at) =>
        s"""{"type":"PaymentSettled","paymentId":"$paymentId","invoiceId":"$invoiceId","at":"$at"}"""
      case BillingEvent.KybSubmitted(businessId, at) =>
        s"""{"type":"KybSubmitted","businessId":"$businessId","at":"$at"}"""
    }
  }
}
// =======================
// Main (wiring + structured concurrency)
// =======================

object MainApp extends ZIOAppDefault {

  private val deps =
    InvoiceStore.inMemory ++
      PaymentStore.inMemory ++
      BusinessStore.inMemory ++
      IdempotencyStore.inMemory ++
      Events.live ++
      PaymentProcessor.stub ++
      Kyb.stub ++
      Accounting.stub ++
      Email.stub ++
      Webhooks.stub ++
      AppConfig.live ++
      Billing.live

  override def run: ZIO[Any, Any, Any] = ???
  // ZIO.scoped {
  //   for {
  //     _ <- ZIO.logInfo("Starting Billing Platform (ZIO / Scala 3)")
  //     _ <- Workers.accountingSync.provideSomeLayer(deps).forkScoped
  //     _ <- Workers.webhookDispatch.provideSomeLayer(deps).forkScoped
  //     _ <- Workers.reconciliation.provideSomeLayer(deps).forkScoped
  //     _ <- Workers.kybSync.provideSomeLayer(deps).forkScoped
  //     _ <- ZIO.logInfo("All workers started. API server would start here in production.")
  //     _ <- ZIO.logInfo("Simulating API server running... (replace with actual server in production)")

  //     // In production, replace with Server.serve(HttpApi.routes).provide(deps)
  //     _ <- ZIO.never
  //   } yield ()
  // }

//   override def run: ZIO[Any, Any, Any] =
//     ZIO.scoped {
//       for {
//         _ <- ZIO.logInfo("Starting Billing Platform (ZIO / Scala 3)")
//         _ <- Workers.accountingSync.provideSomeLayer(deps).forkScoped
//         _ <- Workers.webhookDispatch.provideSomeLayer(deps).forkScoped
//         _ <- Workers.reconciliation.provideSomeLayer(deps).forkScoped

//         _ <- Server.serve(HttpApi.routes).provide(
//           //Server.default,
//           deps
//         )
//       } yield ()
//    }

}
