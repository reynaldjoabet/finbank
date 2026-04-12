package migrantbank.service

import migrantbank.config.AppConfig
import migrantbank.db.Db
import migrantbank.domain.*
import migrantbank.integrations.*
import migrantbank.repo.*
import migrantbank.security.*
import zio.*
import zio.json.*
import java.time.Instant
import java.util.UUID

trait RegistrationService {
  def start(
      profile: UserProfile,
      correlationId: String
  ): IO[AppError, (UUID, KycStatus)]
  def confirm(
      userId: UUID,
      smsCode: String,
      password: String,
      correlationId: String
  ): IO[AppError, RegistrationService.ConfirmResponse]
}

object RegistrationService {

  final case class ConfirmResponse(
      user: User,
      account: Account,
      cards: List[Card],
      tokens: AuthTokens
  ) derives JsonEncoder,
        JsonDecoder

  val live: ZLayer[
    Db & AppConfig & Crypto & PasswordHasher & JwtService & AlloyClient &
      VouchedClient & MbanqClient & SmsClient,
    Nothing,
    RegistrationService
  ] =
    ZLayer.fromFunction {
      (
          db: Db,
          cfg: AppConfig,
          crypto: Crypto,
          hasher: PasswordHasher,
          jwt: JwtService,
          alloy: AlloyClient,
          vouched: VouchedClient,
          mb: MbanqClient,
          sms: SmsClient
      ) =>
        new RegistrationService {

          private def validate(p: UserProfile): IO[AppError, Unit] =
            for {
              _ <- ZIO
                .fail(AppError.Validation("First name required"))
                .when(p.firstName.trim.isEmpty)
              _ <- ZIO
                .fail(AppError.Validation("Last name required"))
                .when(p.lastName.trim.isEmpty)
              _ <- ZIO
                .fail(AppError.Validation("Phone required"))
                .when(p.phone.trim.isEmpty)
              _ <- ZIO
                .fail(AppError.Validation("Address required"))
                .when(p.address.trim.isEmpty)
              _ <- ZIO
                .fail(AppError.Validation("SSN required"))
                .when(p.ssn.trim.isEmpty)
            } yield ()

          private def last4(ssn: String): String =
            ssn.filter(_.isDigit).takeRight(4) match {
              case s if s.length == 4 => s
              case _                  => "0000"
            }

          override def start(
              profile: UserProfile,
              correlationId: String
          ): IO[AppError, (UUID, KycStatus)] =
            for {
              _ <- validate(profile)
              existing <- db.query {
                UserRepo.findByPhone(profile.phone)
              }
              _ <- ZIO
                .fail(AppError.Conflict("User already exists"))
                .when(existing.isDefined)

              userId <- Random.nextUUID
              ssnEnc <- crypto.encryptUtf8(profile.ssn)
              ssnL4 = last4(profile.ssn)

              // KYC Orchestration with retries and timeout
              kycDecision <- alloy
                .runKyc(profile)
                .timeoutFail(AppError.ProviderUnavailable("Alloy timeout"))(
                  3.seconds
                )
                .retry(Schedule.exponential(200.millis) && Schedule.recurs(3))
                .catchAll(_ => ZIO.succeed(KycStatus.MANUAL_REVIEW_REQUIRED))

              idOk <- vouched
                .verifyIdentity(profile)
                .timeoutFail(AppError.ProviderUnavailable("Vouched timeout"))(
                  3.seconds
                )
                .retry(Schedule.exponential(200.millis) && Schedule.recurs(3))
                .catchAll(_ => ZIO.succeed(false))

              finalKyc =
                if kycDecision == KycStatus.VERIFIED && idOk then
                  KycStatus.VERIFIED
                else if kycDecision == KycStatus.MANUAL_REVIEW_REQUIRED then
                  KycStatus.MANUAL_REVIEW_REQUIRED
                else KycStatus.REJECTED

              _ <- db.transaction {
                UserRepo.insert(userId, profile, ssnEnc, ssnL4, finalKyc)
                AuditRepo.append(
                  "registration_started",
                  Some(userId),
                  correlationId,
                  s"phone=${profile.phone}, kyc=$finalKyc"
                )
              }

              _ <- ZIO.when(finalKyc == KycStatus.VERIFIED) {
                for {
                  code <- Random.nextIntBetween(100000, 999999).map(_.toString)
                  now <- Clock.instant
                  exp = now.plusSeconds(
                    cfg.security.smsCodeTtlMinutes.toLong * 60L
                  )
                  _ <- db.transaction {
                    SmsCodeRepo.upsert(userId, code, exp)
                    AuditRepo.append(
                      "sms_code_generated",
                      Some(userId),
                      correlationId,
                      s"expiresAt=$exp"
                    )
                  }
                  _ <- sms.send(
                    profile.phone,
                    s"Your verification code is $code"
                  )
                } yield ()
              }
            } yield (userId, finalKyc)

          override def confirm(
              userId: UUID,
              smsCode: String,
              password: String,
              correlationId: String
          ): IO[AppError, ConfirmResponse] =
            for {
              _ <- ZIO
                .fail(
                  AppError.Validation("Password must be at least 10 characters")
                )
                .when(password.length < 10)
              now <- Clock.instant
              userRow <- db.query { UserRepo.get(userId) }
              _ <- ZIO
                .fail(AppError.Forbidden("KYC not verified"))
                .when(userRow.kycStatus != KycStatus.VERIFIED)

              ok <- db.query {
                SmsCodeRepo.verify(userId, smsCode, now)
              }
              _ <- ZIO
                .fail(AppError.Validation("Invalid or expired SMS code"))
                .unless(ok)

              pwdHash <- hasher.hash(password)
              access <- jwt.issueAccess(userId, userRow.role)
              refresh <- jwt.issueRefresh()
              tokenId <- Random.nextUUID

              // Get cards from external service first
              vCard <- mb.issueVirtualCard(userId)
              pCard <- mb.orderPhysicalCard(userId)

              exp = now.plusSeconds(
                cfg.security.jwt.refreshTokenDays.toLong * 86400L
              )

              // Atomic database operations for account/card/token setup
              result <- db.transaction {
                UserRepo.updatePassword(userId, pwdHash)
                val acc =
                  AccountRepo.ensureUserAccount(userId, currency = "USD")

                // Card Issuance (Integrates with Mbanq partner)
                CardRepo.insert(vCard)
                CardRepo.insert(pCard)

                RefreshTokenRepo.insert(
                  tokenId,
                  userId,
                  TokenHash.sha256Hex(refresh),
                  exp
                )

                AuditRepo.append(
                  "registration_confirmed",
                  Some(userId),
                  correlationId,
                  "password set, account created, cards issued"
                )
                (acc, List(vCard, pCard))
              }

              (accountRow, cards) = result

            } yield ConfirmResponse(
              user = User(
                id = userRow.id,
                profile = UserProfile(
                  userRow.firstName,
                  userRow.lastName,
                  userRow.dateOfBirth,
                  userRow.phone,
                  userRow.address,
                  "***"
                ),
                kycStatus = userRow.kycStatus,
                role = userRow.role,
                createdAt = userRow.createdAt
              ),
              account = Account(
                id = accountRow.id,
                userId = accountRow.userId,
                accountType = accountRow.accountType,
                name = accountRow.name,
                currency = accountRow.currency,
                balanceMinor = accountRow.balanceMinor,
                createdAt = accountRow.createdAt
              ),
              cards = cards,
              tokens = AuthTokens(access, refresh)
            )
        }
    }
}
