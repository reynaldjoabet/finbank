package tontine.service
import zio.*
import java.time.LocalDate
import java.time.Instant
import java.security.*
import java.util.Base64
import zio.json.EncoderOps
import java.time.ZoneOffset
import tontine.*

trait ScoreService {
  def compute(memberId: MemberId): UIO[TontineScore]
  def exportSignedCredential(
      memberId: MemberId
  ): IO[AppError, String] // JSON string with signature
}
object ScoreService {

  private def scoreFrom(onTimeRate: Double): Int =
    (onTimeRate * 100.0).round.toInt.max(0).min(100)

  val live: ZLayer[ContributionRepo, Nothing, ScoreService] =
    ZLayer.fromFunction { (repo: ContributionRepo) =>
      new ScoreService {

        def compute(memberId: MemberId): UIO[TontineScore] =
          for {
            now <- Clock.instant
            cs <- repo.byMember(memberId)
            // only verified contributions contribute to "bank-verifiable reputation"
            dueCount = cs.size
            verifiedPaid = cs.filter(c =>
              c.status == ContributionStatus.Paid && c.bankReconciled
            )
            onTime = verifiedPaid.count { c =>
              c.paidAt.exists(pa =>
                pa.atZone(ZoneOffset.UTC)
                  .toLocalDate
                  .compareTo(c.dueDate) <= 0
              )
            }
            late = verifiedPaid.size - onTime
            missed = (cs.count(
              _.status != ContributionStatus.Paid
            )) // simplistic MVP
            rate =
              if dueCount == 0 then 0.0
              else onTime.toDouble / dueCount.toDouble
          } yield TontineScore(
            memberId = memberId,
            totalDue = dueCount,
            paidOnTimeVerified = onTime,
            paidLateVerified = late,
            missed = missed,
            onTimeRate = rate,
            score = scoreFrom(rate),
            updatedAt = now
          )

        def exportSignedCredential(memberId: MemberId): IO[AppError, String] =
          for {
            score <- compute(memberId)
            payload = score.toJson
            // MVP: generate ephemeral key each time. Production: load from KMS/HSM/env secret.
            kpGen <- ZIO
              .attempt(KeyPairGenerator.getInstance("Ed25519"))
              .mapError(e => AppError.Validation(e.getMessage))
            kp <- ZIO
              .attempt(kpGen.generateKeyPair())
              .mapError(e => AppError.Validation(e.getMessage))
            sig <- ZIO
              .attempt {
                val s = Signature.getInstance("Ed25519")
                s.initSign(kp.getPrivate)
                s.update(payload.getBytes("UTF-8"))
                Base64.getEncoder.encodeToString(s.sign())
              }
              .mapError(e => AppError.Validation(e.getMessage))
            pub <- ZIO
              .attempt(
                Base64.getEncoder.encodeToString(kp.getPublic.getEncoded)
              )
              .mapError(e => AppError.Validation(e.getMessage))
            credentialJson =
              s"""{"type":"tontine-score-credential","payload":$payload,"signature":"$sig","publicKey":"$pub"}"""
          } yield credentialJson
      }
    }
}
