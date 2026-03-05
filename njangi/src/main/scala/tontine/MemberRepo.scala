package tontine
import zio.*
trait MemberRepo {
  def create(member: Member): UIO[Unit]
  def get(id: MemberId): IO[AppError.NotFound, Member]
}

final case class MemberRepoLive(ref: Ref[Map[MemberId, Member]])
    extends MemberRepo {
  def create(member: Member): UIO[Unit] =
    ref.update(_ + (member.id -> member))

  def get(id: MemberId): IO[AppError.NotFound, Member] =
    ref.get.flatMap { members =>
      ZIO
        .fromOption(members.get(id))
        .orElseFail(AppError.NotFound(s"Member $id not found"))
    }
}

object MemberRepo {
  val layer: ULayer[MemberRepo] =
    ZLayer.fromZIO(Ref.make(Map.empty[MemberId, Member]).map(MemberRepoLive(_)))
}
