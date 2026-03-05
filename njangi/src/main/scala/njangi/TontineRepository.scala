package njangi

import zio.Task
import java.util.UUID

trait TontineRepository {
  def getCircle(circleId: UUID): Task[NjangiCircle]
  def getMember(memberId: UUID): Task[Member]
  def markPaid(memberId: UUID, circleId: UUID): Task[Unit]
}
