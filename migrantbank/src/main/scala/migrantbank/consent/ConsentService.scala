package migrantbank.consent

import migrantbank.security.TokenHash
import migrantbank.domain.AppError
import migrantbank.domain.given_CanEqual_UUID_UUID
import zio.*

import java.time.Instant
import java.util.UUID

/** PSD2-style Open Banking consent service.
  *
  * Lifecycle:
  *   1. TPP calls `create(...)` → receives a raw bearer token (shown once).
  *   2. User authorises the consent via the finbank app (`authorise`).
  *   3. TPP presents the bearer token; `resolve(token)` returns the `Consent`
  *      and the service validates scope + expiry.
  *   4. User or system can `revoke(id)` at any time.
  *
  * Security:
  *   - The raw token is generated once and never stored.
  *   - Only the SHA-256 hash of the token is persisted (same pattern as refresh
  *     tokens in `TokenHash`).
  *   - `resolve(rawToken)` hashes the input and looks up by hash.
  */
trait ConsentService {
  def create(req: CreateConsentRequest): IO[AppError, ConsentTokenResponse]
  def authorise(consentId: ConsentId, userId: UUID): IO[AppError, Consent]
  def resolve(
      rawToken: String,
      requiredScope: ConsentScope
  ): IO[AppError, Consent]
  def revoke(consentId: ConsentId, userId: UUID): IO[AppError, Unit]
  def listByUser(userId: UUID): IO[AppError, List[Consent]]
}

object ConsentService {

  /** Maximum TTL a TPP may request: 90 days. */
  private val MaxTtlSeconds = 90L * 24 * 3600

  val live: ZLayer[Any, Nothing, ConsentService] =
    ZLayer.fromZIO(
      Ref.make(Map.empty[ConsentId, Consent]).map { store =>
        new ConsentService {

          override def create(
              req: CreateConsentRequest
          ): IO[AppError, ConsentTokenResponse] =
            for {
              _ <- ZIO
                .fail(AppError.Validation("ttlSeconds must be > 0"))
                .when(req.ttlSeconds <= 0)
              _ <- ZIO
                .fail(
                  AppError.Validation(
                    s"ttlSeconds must be ≤ $MaxTtlSeconds (90 days)"
                  )
                )
                .when(req.ttlSeconds > MaxTtlSeconds)
              _ <- ZIO
                .fail(AppError.Validation("At least one scope is required"))
                .when(req.scopes.isEmpty)

              id <- ConsentId.random
              now <- Clock.instant
              rawToken <- Random.nextUUID.map(_.toString.replace("-", ""))
              tokenHash = TokenHash.sha256Hex(rawToken)
              consent = Consent(
                id = id,
                userId = req.userId,
                tppClientId = req.tppClientId,
                scopes = req.scopes,
                token = tokenHash,
                expiresAt = now.plusSeconds(req.ttlSeconds),
                createdAt = now,
                revokedAt = None,
                status = ConsentStatus.Pending
              )
              _ <- store.update(_ + (id -> consent))
              _ <- ZIO.logInfo(
                s"[Consent] Created id=${id.value} tpp=${req.tppClientId} " +
                  s"scopes=${req.scopes.mkString(",")} ttl=${req.ttlSeconds}s"
              )
            } yield ConsentTokenResponse(
              consentId = id,
              token = rawToken,
              scopes = req.scopes,
              expiresAt = consent.expiresAt
            )

          override def authorise(
              consentId: ConsentId,
              userId: UUID
          ): IO[AppError, Consent] =
            for {
              c <- getById(consentId)
              _ <- ZIO
                .fail(
                  AppError
                    .Forbidden("You are not the resource owner of this consent")
                )
                .when(c.userId != userId)
              _ <- ZIO
                .fail(AppError.Conflict("Consent is not in Pending state"))
                .when(c.status != ConsentStatus.Pending)
              updated = c.copy(status = ConsentStatus.Active)
              _ <- store.update(_ + (consentId -> updated))
              _ <- ZIO.logInfo(
                s"[Consent] Authorised id=${consentId.value} user=$userId"
              )
            } yield updated

          override def resolve(
              rawToken: String,
              requiredScope: ConsentScope
          ): IO[AppError, Consent] =
            for {
              hash <- ZIO.succeed(TokenHash.sha256Hex(rawToken))
              now <- Clock.instant
              all <- store.get
              c <- ZIO
                .fromOption(all.values.find(_.token == hash))
                .orElseFail(
                  AppError.Unauthorized("Invalid or unknown consent token")
                )
              _ <- ZIO
                .fail(AppError.Unauthorized("Consent has been revoked"))
                .when(c.status == ConsentStatus.Revoked)
              _ <- ZIO
                .fail(AppError.Unauthorized("Consent has not been authorised"))
                .when(c.status == ConsentStatus.Pending)
              _ <- ZIO
                .fail(AppError.Unauthorized("Consent token has expired"))
                .when(c.expiresAt.isBefore(now))
              _ <- ZIO
                .fail(
                  AppError.Forbidden(
                    s"Consent does not include scope ${requiredScope}"
                  )
                )
                .when(!c.scopes.contains(requiredScope))
            } yield c

          override def revoke(
              consentId: ConsentId,
              userId: UUID
          ): IO[AppError, Unit] =
            for {
              c <- getById(consentId)
              _ <- ZIO
                .fail(
                  AppError
                    .Forbidden("You are not the resource owner of this consent")
                )
                .when(c.userId != userId)
              now <- Clock.instant
              updated = c
                .copy(status = ConsentStatus.Revoked, revokedAt = Some(now))
              _ <- store.update(_ + (consentId -> updated))
              _ <- ZIO.logInfo(
                s"[Consent] Revoked id=${consentId.value} user=$userId"
              )
            } yield ()

          override def listByUser(userId: UUID): IO[AppError, List[Consent]] =
            store.get.map(_.values.filter(_.userId == userId).toList)

          private def getById(id: ConsentId): IO[AppError, Consent] =
            store.get.flatMap { m =>
              ZIO
                .fromOption(m.get(id))
                .orElseFail(AppError.NotFound(s"Consent ${id.value} not found"))
            }
        }
      }
    )
}
