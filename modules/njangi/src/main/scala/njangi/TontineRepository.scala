package njangi

import java.util.UUID

import zio.Task

trait TontineRepository {

  def getCircle(circleId: UUID): Task[NjangiCircle]
  def getMember(memberId: UUID): Task[Member]
  def markPaid(memberId: UUID, circleId: UUID): Task[Unit]

}
