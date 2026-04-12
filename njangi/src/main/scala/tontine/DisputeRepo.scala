package tontine

import zio.*

trait DisputeRepo {
  def create(d: Dispute): UIO[Unit]
  def get(id: DisputeId): IO[AppError.NotFound, Dispute]
  def byCircle(circleId: CircleId): UIO[List[Dispute]]
  def byMember(memberId: MemberId): UIO[List[Dispute]]
  def update(d: Dispute): IO[AppError.NotFound, Unit]

  def createPenalty(p: Penalty): UIO[Unit]
  def penaltiesByMember(memberId: MemberId): UIO[List[Penalty]]
}

final case class DisputeRepoLive(
    disputes: Ref[Map[DisputeId, Dispute]],
    penalties: Ref[Map[PenaltyId, Penalty]]
) extends DisputeRepo {

  override def create(d: Dispute): UIO[Unit] =
    disputes.update(_ + (d.id -> d))

  override def get(id: DisputeId): IO[AppError.NotFound, Dispute] =
    disputes.get.flatMap { m =>
      ZIO
        .fromOption(m.get(id))
        .orElseFail(AppError.NotFound(s"Dispute $id not found"))
    }

  override def byCircle(circleId: CircleId): UIO[List[Dispute]] =
    disputes.get.map(_.values.filter(_.circleId == circleId).toList)

  override def byMember(memberId: MemberId): UIO[List[Dispute]] =
    disputes.get.map(_.values.filter(_.memberId == memberId).toList)

  override def update(d: Dispute): IO[AppError.NotFound, Unit] =
    disputes
      .update { m =>
        if m.contains(d.id) then m + (d.id -> d) else m
      }
      .flatMap(_ => get(d.id).unit)

  override def createPenalty(p: Penalty): UIO[Unit] =
    penalties.update(_ + (p.id -> p))

  override def penaltiesByMember(memberId: MemberId): UIO[List[Penalty]] =
    penalties.get.map(_.values.filter(_.memberId == memberId).toList)
}

object DisputeRepo {
  val layer: ULayer[DisputeRepo] =
    ZLayer.fromZIO {
      for {
        ds <- Ref.make(Map.empty[DisputeId, Dispute])
        ps <- Ref.make(Map.empty[PenaltyId, Penalty])
      } yield DisputeRepoLive(ds, ps)
    }
}
