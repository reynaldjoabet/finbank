package tontine.service

import java.security.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

import zio.*
import zio.json.EncoderOps
import tontine.*

trait CircleService {

  def createCircle(name: String, bankAccountRef: String): UIO[Circle]
  def addMember(circleId: CircleId, memberId: MemberId): IO[AppError, Circle]
  def getCircle(circleId: CircleId): IO[AppError, Circle]

}

object CircleService {

  val live: ZLayer[CircleRepo & AuditRepo, Nothing, CircleService] =
    ZLayer.fromFunction { (repo: CircleRepo, audit: AuditRepo) =>
      new CircleService {
        def createCircle(name: String, bankAccountRef: String): UIO[Circle] =
          for {
            id    <- CircleId.random
            now   <- Clock.instant
            circle = Circle(id, name, now, Set.empty, bankAccountRef)
            _     <- repo.create(circle)
            _     <- audit.append(s"circle.created id=${id.value} name=$name")
          } yield circle

        def addMember(
            circleId: CircleId,
            memberId: MemberId
        ): IO[AppError, Circle] =
          for {
            circle <- repo.get(circleId).mapError(identity)
            updated = circle.copy(members = circle.members + memberId)
            _      <- repo.update(updated).mapError(identity)
            _      <- audit.append(
                   s"circle.member_added circle=${circleId.value} member=${memberId.value}"
                 )
          } yield updated

        def getCircle(circleId: CircleId): IO[AppError, Circle] =
          repo.get(circleId).mapError(identity)
      }
    }

}
