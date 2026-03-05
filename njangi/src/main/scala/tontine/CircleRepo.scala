package tontine
import zio.*
trait CircleRepo {
  def create(circle: Circle): UIO[Unit]
  def get(id: CircleId): IO[AppError.NotFound, Circle]
  def update(circle: Circle): IO[AppError.NotFound, Unit]
}

final case class CircleRepoLive(ref: Ref[Map[CircleId, Circle]])
    extends CircleRepo {
  def create(circle: Circle): UIO[Unit] =
    ref.update(_ + (circle.id -> circle))

  def get(id: CircleId): IO[AppError.NotFound, Circle] =
    ref.get.flatMap { circles =>
      ZIO
        .fromOption(circles.get(id))
        .orElseFail(AppError.NotFound(s"Circle $id not found"))
    }

  def update(circle: Circle): IO[AppError.NotFound, Unit] =
    ref
      .update { circles =>
        if (circles.contains(circle.id)) circles + (circle.id -> circle)
        else circles
      }
      .flatMap { _ =>
        get(circle.id).unit
      }
}

object CircleRepo {
  val layer: ULayer[CircleRepo] =
    ZLayer.fromZIO(Ref.make(Map.empty[CircleId, Circle]).map(CircleRepoLive(_)))
}
