package njangi
import zio.*
import java.util.UUID
abstract class NjangiRepository {
  def createCircle(name: String, creatorId: UUID): Task[UUID]
  def addMember(circleId: UUID, memberId: UUID): Task[Unit]
  def recordContribution(
      circleId: UUID,
      memberId: UUID,
      amount: BigDecimal
  ): Task[Unit]
  def getCircleState(circleId: UUID): Task[CircleState]
  def getMember(memberId: UUID): Task[Member]
  def markPaid(memberId: UUID, circleId: UUID): Task[Unit]
}
