package migrantbank.webhook
import migrantbank.domain.AppError
import zio.*
import zio.json.*
import java.time.Instant
import java.util.UUID

// ── Domain events ────────────────────────────────────────────────────────────

/** Typed domain events emitted by finbank services.
  *
  * Each event carries enough context for a third-party webhook consumer to act
  * without making additional API calls.
  */
enum DomainEvent derives CanEqual {
  case ContributionMade(
      memberId: UUID,
      circleId: UUID,
      contributionId: UUID,
      amountMinor: Long,
      currency: String,
      occurredAt: Instant
  )
  case TransferSettled(
      transferId: UUID,
      fromUserId: UUID,
      toUserId: Option[UUID],
      amountMinor: Long,
      currency: String,
      occurredAt: Instant
  )
  case LoanApproved(
      loanId: UUID,
      userId: UUID,
      principalMinor: Long,
      currency: String,
      dueDateIso: String,
      occurredAt: Instant
  )
  case KycStatusChanged(
      userId: UUID,
      newStatus: String,
      occurredAt: Instant
  )
  case ConsentGranted(
      consentId: UUID,
      userId: UUID,
      tppClientId: String,
      scopes: List[String],
      occurredAt: Instant
  )
  case BnplPlanCreated(
      planId: UUID,
      userId: UUID,
      merchantRef: String,
      amountMinor: Long,
      currency: String,
      instalmentCount: Int,
      occurredAt: Instant
  )
}

object DomainEvent {
  given JsonEncoder[Instant] = JsonEncoder.string.contramap(_.toString)
  given JsonDecoder[Instant] = JsonDecoder.string.map(Instant.parse)
  given JsonCodec[DomainEvent] = DeriveJsonCodec.gen[DomainEvent]
}

// ── Webhook subscription ─────────────────────────────────────────────────────

opaque type SubscriptionId = UUID
object SubscriptionId {
  def apply(u: UUID): SubscriptionId = u
  def unapply(id: SubscriptionId): UUID = id
  def random: UIO[SubscriptionId] = Random.nextUUID

  extension (id: SubscriptionId) def value: UUID = id

  given CanEqual[SubscriptionId, SubscriptionId] = CanEqual.derived
  given JsonEncoder[SubscriptionId] = JsonEncoder.uuid
  given JsonDecoder[SubscriptionId] = JsonDecoder.uuid
}

/** A webhook subscription registered by a third-party (TPP or internal
  * service).
  *
  * @param eventTypes
  *   Which event types to deliver. Empty set = all events.
  * @param secret
  *   HMAC-SHA256 signing secret for payload verification. Stored hashed; the
  *   raw secret is shown only at creation time.
  */
final case class WebhookSubscription(
    id: SubscriptionId,
    tppClientId: String,
    targetUrl: String,
    eventTypes: Set[String], // empty = subscribe to all
    secretHash: String, // SHA-256 of the raw signing secret
    active: Boolean,
    createdAt: Instant
) derives JsonCodec

/** A delivery attempt for a single domain event.
  */
enum DeliveryStatus derives CanEqual, JsonCodec {
  case Pending, Delivered, Failed
}

final case class WebhookDelivery(
    id: UUID,
    subscriptionId: SubscriptionId,
    eventType: String,
    payloadJson: String,
    status: DeliveryStatus,
    httpStatus: Option[Int],
    attemptedAt: Instant
) derives JsonCodec

// ── Service ──────────────────────────────────────────────────────────────────

/** Outbound webhook service.
  *
  * Partners register endpoint URLs via `subscribe`. Every time `emit` is called
  * (by a service that just completed a mutation), the webhook service fans out
  * to all matching subscribers with exponential-backoff retries.
  *
  * Payload format:
  * {{{
  *   POST <targetUrl>
  *   Content-Type: application/json
  *   X-Finbank-Event: transfer.settled
  *   X-Finbank-Delivery: <deliveryId>
  *   X-Finbank-Signature: sha256=<hex>
  *
  *   {"eventType":"TransferSettled","transferId":"...", ...}
  * }}}
  *
  * Signature verification: `HMAC-SHA256(secret, body)` — same as GitHub
  * webhooks.
  */
trait WebhookService {
  def subscribe(
      tppClientId: String,
      targetUrl: String,
      eventTypes: Set[String],
      rawSecret: String
  ): IO[AppError, WebhookSubscription]

  def unsubscribe(id: SubscriptionId, tppClientId: String): IO[AppError, Unit]

  def emit(event: DomainEvent): UIO[Unit]

  def deliveries(
      subscriptionId: SubscriptionId
  ): IO[AppError, List[WebhookDelivery]]
}

object WebhookService {

  val live: ZLayer[Any, Nothing, WebhookService] =
    ZLayer.fromZIO {
      for {
        subs <- Ref.make(Map.empty[SubscriptionId, WebhookSubscription])
        dlvs <- Ref.make(Map.empty[SubscriptionId, List[WebhookDelivery]])
      } yield new WebhookServiceLive(subs, dlvs)
    }

  private final class WebhookServiceLive(
      subs: Ref[Map[SubscriptionId, WebhookSubscription]],
      dlvs: Ref[Map[SubscriptionId, List[WebhookDelivery]]]
  ) extends WebhookService {

    override def subscribe(
        tppClientId: String,
        targetUrl: String,
        eventTypes: Set[String],
        rawSecret: String
    ): IO[AppError, WebhookSubscription] =
      for {
        _ <- ZIO
          .fail(AppError.Validation("targetUrl must start with https://"))
          .when(!targetUrl.startsWith("https://"))
        id <- SubscriptionId.random
        now <- Clock.instant
        secretH = sha256Hex(rawSecret)
        sub = WebhookSubscription(
          id = id,
          tppClientId = tppClientId,
          targetUrl = targetUrl,
          eventTypes = eventTypes,
          secretHash = secretH,
          active = true,
          createdAt = now
        )
        _ <- subs.update(_ + (id -> sub))
        _ <- ZIO.logInfo(
          s"[Webhook] Subscribed id=${id.value} tpp=$tppClientId url=$targetUrl"
        )
      } yield sub

    override def unsubscribe(
        id: SubscriptionId,
        tppClientId: String
    ): IO[AppError, Unit] =
      for {
        all <- subs.get
        sub <- ZIO
          .fromOption(all.get(id))
          .orElseFail(AppError.NotFound(s"Subscription ${id.value} not found"))
        _ <- ZIO
          .fail(AppError.Forbidden("Not your subscription"))
          .when(sub.tppClientId != tppClientId)
        _ <- subs.update(_ + (id -> sub.copy(active = false)))
        _ <- ZIO.logInfo(s"[Webhook] Unsubscribed id=${id.value}")
      } yield ()

    override def emit(event: DomainEvent): UIO[Unit] = {
      val eventType = event.getClass.getSimpleName
      val payloadJson = event.toJson
      for {
        allSubs <- subs.get
        matched = allSubs.values.filter { s =>
          s.active && (s.eventTypes.isEmpty || s.eventTypes.contains(eventType))
        }.toList
        _ <- ZIO.foreachParDiscard(matched) { sub =>
          deliver(sub, eventType, payloadJson)
            .retry(
              Schedule.exponential(1.second) &&
                Schedule.recurs(3)
            )
            .catchAll { e =>
              ZIO.logWarning(
                s"[Webhook] Final delivery failure sub=${sub.id.value} event=$eventType: $e"
              )
            }
        }
      } yield ()
    }

    override def deliveries(
        subscriptionId: SubscriptionId
    ): IO[AppError, List[WebhookDelivery]] =
      dlvs.get.map(_.getOrElse(subscriptionId, List.empty))

    private def deliver(
        sub: WebhookSubscription,
        eventType: String,
        payloadJson: String
    ): IO[Throwable, Unit] =
      for {
        deliveryId <- Random.nextUUID
        now <- Clock.instant
        // Compute HMAC-SHA256 signature
        sig = hmacSha256Hex(sub.secretHash, payloadJson)
        // --- stub: replace with sttp / zio-http HTTP POST to sub.targetUrl ---
        _ <- ZIO.logInfo(
          s"[Webhook] Delivering event=$eventType to=${sub.targetUrl} " +
            s"delivery=$deliveryId sig=$sig"
        )
        record = WebhookDelivery(
          id = deliveryId,
          subscriptionId = sub.id,
          eventType = eventType,
          payloadJson = payloadJson,
          status = DeliveryStatus.Delivered,
          httpStatus = Some(200),
          attemptedAt = now
        )
        _ <- dlvs.update { m =>
          val existing = m.getOrElse(sub.id, List.empty)
          m + (sub.id -> (record :: existing))
        }
      } yield ()

    private def sha256Hex(input: String): String = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.digest(input.getBytes("UTF-8"))
        .map(b => f"${b & 0xff}%02x")
        .mkString
    }

    private def hmacSha256Hex(secretHash: String, payload: String): String = {
      val mac = javax.crypto.Mac.getInstance("HmacSHA256")
      val key = new javax.crypto.spec.SecretKeySpec(
        secretHash.getBytes("UTF-8"),
        "HmacSHA256"
      )
      mac.init(key)
      mac
        .doFinal(payload.getBytes("UTF-8"))
        .map(b => f"${b & 0xff}%02x")
        .mkString
    }
  }
}
