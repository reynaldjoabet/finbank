package migrantbank.ratelimit

import migrantbank.domain.AppError
import zio.*

import java.util.concurrent.TimeUnit
trait RateLimiter {
  def check(key: String, limitPerMinute: Int): IO[AppError.RateLimited, Unit]
}
object RateLimiter {

  /** Fixed-window per-minute limiter */
  val live: ZLayer[Any, Nothing, RateLimiter] =
    ZLayer.fromZIO(
      Ref.make(Map.empty[(String, Long), Int]).map { ref =>
        new RateLimiter {
          override def check(
              key: String,
              limitPerMinute: Int
          ): IO[AppError.RateLimited, Unit] =
            for {
              now <- Clock.currentTime(TimeUnit.MILLISECONDS)
              window = now / 60000L
              count <- ref.modify { m =>
                val k = (key, window)
                val next = m.getOrElse(k, 0) + 1
                (next, m.updated(k, next))
              }
              _ <- ZIO
                .fail(AppError.RateLimited("Too many requests"))
                .when(count > limitPerMinute)
            } yield ()
        }
      }
    )
}
