package migrantbank.consent

import zio.json.*
import java.time.Instant
import java.util.UUID

// ── Opaque IDs ─────────────────────────────────────────────────────────────

opaque type ConsentId = UUID
object ConsentId {
  def apply(uuid: UUID): ConsentId = uuid
  def unapply(id: ConsentId): UUID = id
  def random: zio.UIO[ConsentId] = zio.Random.nextUUID

  extension (id: ConsentId) def value: UUID = id

  given CanEqual[ConsentId, ConsentId] = CanEqual.derived
  given JsonEncoder[ConsentId] = JsonEncoder.uuid
  given JsonDecoder[ConsentId] = JsonDecoder.uuid
}

// ── Scopes ──────────────────────────────────────────────────────────────────

/** Granular data-sharing scopes — inspired by PSD2 / Open Banking UK.
  *
  * A consent token may carry one or more scopes. Each scope gates a specific
  * resource:
  *
  * `ReadBalance` → GET /v1/accounts/{id}/balance `ReadTransactions` → GET
  * /v1/accounts/{id}/transactions `ReadProfile` → GET /v1/users/{id}/profile
  * (name, phone) `InitiatePayment` → POST /v1/transfers (on behalf of the user)
  * `ReadTontineScore` → GET /v1/members/{id}/score (credit signal)
  */
enum ConsentScope derives JsonCodec, CanEqual {
  case ReadBalance
  case ReadTransactions
  case ReadProfile
  case InitiatePayment
  case ReadTontineScore
}

// ── Entity ──────────────────────────────────────────────────────────────────

enum ConsentStatus derives JsonCodec, CanEqual {
  case Pending // Created but not yet authorised by the user
  case Active // User authorised; token is valid
  case Revoked // User or system revoked
  case Expired // Past `expiresAt`
}

/** A consent grant.
  *
  * The `token` is an opaque bearer secret handed to the third-party (TPP). The
  * TPP includes it as `Authorization: Bearer <token>` on resource calls. The
  * `ConsentService` resolves the token → `Consent` and checks scope + expiry.
  */
final case class Consent(
    id: ConsentId,
    /** The resource owner (bank customer) who granted the consent. */
    userId: UUID,
    /** The third-party application that requested the consent. */
    tppClientId: String,
    scopes: Set[ConsentScope],
    token: String, // hashed SHA-256 of the raw bearer token
    expiresAt: Instant,
    createdAt: Instant,
    revokedAt: Option[Instant],
    status: ConsentStatus
) derives JsonCodec

// ── DTOs ────────────────────────────────────────────────────────────────────

final case class CreateConsentRequest(
    userId: UUID,
    tppClientId: String,
    scopes: Set[ConsentScope],
    /** Validity in seconds (max 90 days). */
    ttlSeconds: Long
) derives JsonCodec

final case class ConsentTokenResponse(
    consentId: ConsentId,
    /** Raw bearer token — shown ONCE; never stored in plain-text. */
    token: String,
    scopes: Set[ConsentScope],
    expiresAt: Instant
) derives JsonCodec
