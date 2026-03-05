package tontine
import zio.*
trait ContributionRepo {
  def create(c: Contribution): UIO[Unit]
  def get(id: ContributionId): IO[AppError.NotFound, Contribution]
  def byCircle(circleId: CircleId): UIO[List[Contribution]]
  def byMember(memberId: MemberId): UIO[List[Contribution]]
  def update(c: Contribution): IO[AppError.NotFound, Unit]
}

final case class ContributionRepoLive(
    ref: Ref[Map[ContributionId, Contribution]]
) extends ContributionRepo {
  def create(c: Contribution): UIO[Unit] =
    ref.update(_ + (c.id -> c))

  def get(id: ContributionId): IO[AppError.NotFound, Contribution] =
    ref.get.flatMap { contributions =>
      ZIO
        .fromOption(contributions.get(id))
        .orElseFail(AppError.NotFound(s"Contribution $id not found"))
    }

  def byCircle(circleId: CircleId): UIO[List[Contribution]] =
    ref.get.map(_.values.filter(_.circleId == circleId).toList)

  def byMember(memberId: MemberId): UIO[List[Contribution]] =
    ref.get.map(_.values.filter(_.memberId == memberId).toList)

  def update(c: Contribution): IO[AppError.NotFound, Unit] =
    ref
      .update { contributions =>
        if (contributions.contains(c.id)) contributions + (c.id -> c)
        else contributions
      }
      .flatMap { _ =>
        get(c.id).unit
      }
}

object ContributionRepo {
  val layer =
    ZLayer.fromZIO(
      Ref
        .make(Map.empty[ContributionId, Contribution])
        .map(ContributionRepoLive(_))
    )
}
