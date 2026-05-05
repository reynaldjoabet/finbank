object Hello extends Greeting with App {
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}

import cats.effect.{Deferred, IO, Resource}
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import fs2.Chunk
import fs2.concurrent.Channel

import java.util.NoSuchElementException
import java.util.concurrent.{CompletableFuture, Executor}
import scala.concurrent.duration.FiniteDuration

/** An AsyncCacheLoader that buffers individual key requests until either
  * `maxSize` keys are queued or `maxTime` has elapsed, then issues a single
  * bulk lookup. Use when one round-trip for N keys is materially cheaper than N
  * round-trips.
  */
final class CoalescingBulkLoader[K, V] private (
    channel: Channel[IO, (K, Deferred[IO, Either[Throwable, V]])],
    dispatcher: Dispatcher[IO]
) extends AsyncCacheLoader[K, V] {

  override def asyncLoad(key: K, executor: Executor): CompletableFuture[V] = {
    val io =
      for {
        slot <- Deferred[IO, Either[Throwable, V]]
        res <- channel.send(key -> slot)
        _ <- res match {
          case Right(()) => IO.unit
          case Left(_)   =>
            IO.raiseError(
              IllegalStateException("CoalescingBulkLoader is closed")
            )
        }
        v <- slot.get.rethrow
      } yield v
    dispatcher.unsafeToCompletableFuture(io)
  }
}
object CoalescingBulkLoader {

  /** @param load
    *   bulk loader. Keys absent from the returned Map fail their individual
    *   `asyncLoad` with NoSuchElementException — Caffeine then propagates that
    *   to the caller and does not cache.
    */
  def resource[K, V](
      maxSize: Int,
      maxTime: FiniteDuration,
      parallelism: Int,
      load: Set[K] => IO[Map[K, V]]
  ): Resource[IO, CoalescingBulkLoader[K, V]] =
    for {
      dispatcher <- Dispatcher.parallel[IO]
      channel <- Resource.eval(
        Channel.unbounded[IO, (K, Deferred[IO, Either[Throwable, V]])]
      )
      _ <- channel.stream
        .groupWithin(maxSize, maxTime)
        .parEvalMapUnordered(parallelism)(processBatch(load))
        .compile
        .drain
        .background
    } yield CoalescingBulkLoader(channel, dispatcher)

  private def processBatch[K, V](load: Set[K] => IO[Map[K, V]])(
      batch: Chunk[(K, Deferred[IO, Either[Throwable, V]])]
  ): IO[Unit] = {
    // Multiple requests for the same key in one window share a single lookup.
    val grouped = batch.toList.groupMap(_._1)(_._2)
    load(grouped.keySet).attempt.flatMap {
      case Right(results) =>
        grouped.toList.traverse_ { (k, slots) =>
          val outcome = results
            .get(k)
            .toRight(
              NoSuchElementException(s"bulk load returned no entry for $k")
            )
          slots.traverse_(_.complete(outcome))
        }
      case Left(t) =>
        grouped.values.flatten.toList.traverse_(_.complete(Left(t)))
    }
  }
}
