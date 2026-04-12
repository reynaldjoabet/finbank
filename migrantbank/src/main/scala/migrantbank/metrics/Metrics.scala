package migrantbank.metrics

import zio.*

trait Metrics {
  def inc(name: String): UIO[Unit]
  def snapshot: UIO[Map[String, Long]]
}
object Metrics {
  val live: ZLayer[Any, Nothing, Metrics] =
    ZLayer.fromZIO(Ref.make(Map.empty[String, Long]).map { ref =>
      new Metrics {
        override def inc(name: String): UIO[Unit] =
          ref.update(m => m.updated(name, m.getOrElse(name, 0L) + 1L)).unit

        override def snapshot: UIO[Map[String, Long]] =
          ref.get
      }
    })
}
