package coinstar.wallet.service

import coinstar.wallet.domain.{DomainError, UserId}
import coinstar.wallet.persistence.{IdempotencyRepo, IdempotencyRow}
import zio.*
import zio.json.*

import java.time.Instant

trait IdempotencyService {

  /** Generic idempotency wrapper.
    *
    *   - key uniqueness is per-user
    *   - requestHash must remain stable for a given key
    *   - stores ONLY successful responses (you can extend this to store
    *     failures too)
    */
  def run[R, A: JsonEncoder: JsonDecoder](
      userId: UserId,
      key: String,
      requestHash: String
  )(fa: ZIO[R, DomainError, A]): ZIO[R, DomainError, A]
}

object IdempotencyService {
  def run[R, A: JsonEncoder: JsonDecoder](
      userId: UserId,
      key: String,
      requestHash: String
  )(fa: ZIO[R, DomainError, A]): ZIO[IdempotencyService & R, DomainError, A] =
    ZIO.serviceWithZIO[IdempotencyService](_.run(userId, key, requestHash)(fa))
}

final class IdempotencyServiceLive(repo: IdempotencyRepo)
    extends IdempotencyService {

  override def run[R, A: JsonEncoder: JsonDecoder](
      userId: UserId,
      key: String,
      requestHash: String
  )(fa: ZIO[R, DomainError, A]): ZIO[R, DomainError, A] =
    repo.get(userId.value, key).flatMap {
      case Some(row) =>
        if row.requestHash != requestHash then
          ZIO.fail(
            DomainError.Conflict("Idempotency-Key reuse with different payload")
          )
        else
          ZIO
            .fromEither(row.responseJson.fromJson[A])
            .mapError(err =>
              DomainError
                .Internal(s"Stored idempotency response is not decodable: $err")
            )
      case None =>
        fa.tap { a =>
          val json = a.toJson
          repo.put(
            IdempotencyRow(userId.value, key, requestHash, json, Instant.now())
          )
        }
    }
}

object IdempotencyServiceLive {
  val layer: ZLayer[IdempotencyRepo, Nothing, IdempotencyService] =
    ZLayer.fromFunction(new IdempotencyServiceLive(_))
}
