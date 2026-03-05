package njangi
import zio._
import java.util.UUID
import java.time.Instant
import java.{util => ju}

trait TontineService {
  def collectContribution(memberId: UUID, circleId: UUID): Task[Boolean]
  def processRotation(
      circleId: UUID
  ): Task[UUID] // Returns the ID of the member paid
}

case class TontineServiceLive(
    momoClient: MoMoClient,
    repository: TontineRepository
) extends TontineService {

  override def collectContribution(
      memberId: UUID,
      circleId: UUID
  ): Task[Boolean] = {
    for {
      circle <- repository.getCircle(circleId)
      member <- repository.getMember(memberId)

      // Trigger the "STK Push" via GIMAC/MTN/Orange API
      paymentStatus <- momoClient.requestPayment(
        phone = member.momoNumber,
        amount = circle.contributionAmount
      )

      _ <-
        if (paymentStatus == PaymentStatus.Success) {
          repository.markPaid(memberId, circleId)
        } else {
          ZIO.fail(new Exception("Payment Failed - Notify the Circle"))
        }
    } yield paymentStatus == PaymentStatus.Success
  }
  override def processRotation(circleId: ju.UUID): Task[ju.UUID] = ???
}
