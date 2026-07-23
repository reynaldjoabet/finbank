package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*

trait TaxpayerService {
  def register(
      reg: TaxpayerRegistration,
      principal: Principal
  ): IO[ApiError, Taxpayer]
  def get(id: TaxpayerId, principal: Principal): IO[ApiError, Taxpayer]
  def list(principal: Principal): IO[ApiError, List[Taxpayer]]
}

object TaxpayerService {

  private def generateTan(nowMs: Long): String = {
    val suffix = (nowMs % 1_000_000L).toString.reverse.padTo(6, '0').reverse
    s"TAN-$suffix"
  }

  val live: URLayer[TaxpayerRepo & AuditService & Clock, TaxpayerService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[TaxpayerRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new TaxpayerService {

        override def register(
            reg: TaxpayerRegistration,
            principal: Principal
        ): IO[ApiError, Taxpayer] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => TaxpayerId(u.toString))
            tan <- ZIO.succeed(generateTan(now))
            tp <- repo.create(reg, now, id, tan).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "TAXPAYER_REGISTERED",
              "Taxpayer",
              tp.id.value,
              s"tan=${tp.tan}"
            )
          } yield tp
        }

        override def get(
            id: TaxpayerId,
            principal: Principal
        ): IO[ApiError, Taxpayer] = {
          for {
            opt <- repo.get(id).mapError(ApiError.fromRepo)
            tp <- ZIO
              .fromOption(opt)
              .orElseFail(ApiError.NotFound(s"Taxpayer not found: ${id.value}"))
            _ <- audit.record(
              principal,
              "TAXPAYER_VIEWED",
              "Taxpayer",
              id.value,
              "ok"
            )
          } yield tp
        }

        override def list(
            principal: Principal
        ): IO[ApiError, List[Taxpayer]] = {
          for {
            xs <- repo.list().mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "TAXPAYER_LISTED",
              "Taxpayer",
              "-",
              s"count=${xs.size}"
            )
          } yield xs
        }
      }
    }
}
