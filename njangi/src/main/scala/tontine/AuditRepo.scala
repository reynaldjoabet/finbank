package tontine
import zio.*
trait AuditRepo {
  def append(event: String): UIO[Unit]
}

final case class AuditRepoLive() extends AuditRepo {
  override def append(event: String): UIO[Unit] = ???
}

object AuditRepo {
  val layer: ULayer[AuditRepo] = ZLayer.succeed(AuditRepoLive())
}
