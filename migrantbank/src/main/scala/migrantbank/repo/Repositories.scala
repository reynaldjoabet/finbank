package com.migrantbank.repo

import com.augustnagro.magnum.*
import java.util.UUID
import java.time.Instant
import com.migrantbank.domain.*
import com.migrantbank.security.TokenHash

@Table(PostgresDbType)
case class UserRow(
    @Id id: UUID,
    phone: String,
    firstName: String,
    lastName: String,
    dateOfBirth: String,
    address: String,
    ssnEnc: String,
    ssnLast4: String,
    kycStatus: KycStatus,
    passwordHash: Option[String],
    role: String,
    createdAt: Instant
) derives DbCodec

@Table(PostgresDbType)
case class AccountRow(
    @Id id: UUID,
    userId: Option[UUID],
    accountType: String,
    name: String,
    currency: String,
    balanceMinor: Long,
    createdAt: Instant
) derives DbCodec

object UserRepo {
  private val repo = Repo[UserRow, UserRow, UUID]

  def insert(
      id: UUID,
      profile: UserProfile,
      ssnEnc: String,
      ssnLast4: String,
      kyc: KycStatus
  )(using DbCon): Unit =
    repo.insert(
      UserRow(
        id,
        profile.phone,
        profile.firstName,
        profile.lastName,
        profile.dateOfBirth,
        profile.address,
        ssnEnc,
        ssnLast4,
        kyc,
        None,
        "user",
        Instant.now()
      )
    )

  def findByPhone(phone: String)(using DbCon): Option[UserRow] =
    sql"SELECT * FROM users WHERE phone = $phone"
      .query[UserRow]
      .run()
      .headOption

  def findById(id: UUID)(using DbCon): Option[UserRow] =
    repo.findById(id)

  def get(id: UUID)(using DbCon): UserRow =
    findById(id).getOrElse(
      throw new RuntimeException(s"User $id not found")
    )

  def updateKyc(id: UUID, status: KycStatus)(using DbCon): Unit =
    sql"UPDATE users SET kyc_status = ${status.toString} WHERE id = $id".update
      .run()

  def updatePassword(id: UUID, hash: String)(using DbCon): Unit =
    sql"UPDATE users SET password_hash = $hash WHERE id = $id".update.run()

  def listPendingKyc()(using DbCon): List[UserRow] =
    sql"SELECT * FROM users WHERE kyc_status IN ('PENDING','MANUAL_REVIEW_REQUIRED') ORDER BY created_at DESC LIMIT 200"
      .query[UserRow]
      .run()
      .toList
}

object SmsCodeRepo {
  def upsert(userId: UUID, code: String, expiresAt: Instant)(using
      DbCon
  ): Unit =
    sql"""
      INSERT INTO sms_codes (user_id, code_hash, expires_at) VALUES ($userId, ${TokenHash
        .sha256Hex(code)}, $expiresAt)
      ON CONFLICT (user_id) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, created_at = now()
    """.update.run()

  def verify(userId: UUID, code: String, now: Instant)(using DbCon): Boolean =
    sql"SELECT code_hash, expires_at FROM sms_codes WHERE user_id = $userId"
      .query[(String, Instant)]
      .run()
      .headOption
      .exists { (hash, exp) =>
        exp.isAfter(now) && hash == TokenHash.sha256Hex(code)
      }
}

object AccountRepo {
  private val repo = Repo[AccountRow, AccountRow, UUID]

  def findByUserForUpdate(userId: UUID)(using DbCon): Option[AccountRow] =
    sql"SELECT * FROM accounts WHERE user_id = $userId AND account_type = 'USER' FOR UPDATE"
      .query[AccountRow]
      .run()
      .headOption

  def getByIdForUpdate(id: UUID)(using DbCon): Option[AccountRow] =
    sql"SELECT * FROM accounts WHERE id = $id FOR UPDATE"
      .query[AccountRow]
      .run()
      .headOption

  def updateBalance(id: UUID, newBalance: Long)(using DbCon): Unit =
    sql"UPDATE accounts SET balance_minor = $newBalance WHERE id = $id".update
      .run()

  def ensureUserAccount(userId: UUID, currency: String)(using
      DbCon
  ): AccountRow = {
    findByUserForUpdate(userId) match {
      case Some(a) => a
      case None    =>
        val id = UUID.randomUUID()
        sql"""INSERT INTO accounts (id, user_id, account_type, name, currency, balance_minor)
              VALUES ($id, $userId, 'USER', 'USER_MAIN', $currency, 0)""".update
          .run()
        getByIdForUpdate(id).getOrElse(
          throw new RuntimeException(s"Account $id not found after insert")
        )
    }
  }

  def getUserAccount(userId: UUID)(using DbCon): Option[AccountRow] =
    sql"SELECT * FROM accounts WHERE user_id = $userId AND account_type = 'USER'"
      .query[AccountRow]
      .run()
      .headOption
}

object TransferRepo {
  def insert(t: Transfer)(using DbCon): Unit =
    sql"""
      INSERT INTO transfers (id, transfer_type, from_user_id, to_user_id, ach_destination, amount_minor, currency, note, status, idempotency_key, risk_flag, risk_reason, created_at)
      VALUES (${t.id}, ${t.transferType.toString}, ${t.fromUserId}, ${t.toUserId}, ${t.achDestination}, ${t.amount.amountMinor}, ${t.amount.currency}, ${t.note}, ${t.status.toString}, ${t.idempotencyKey}, ${t.riskFlag}, ${t.riskReason}, ${t.createdAt})
    """.update.run()

  def sumOutgoingVolume(userId: UUID)(using DbCon): Long =
    sql"SELECT COALESCE(SUM(amount_minor),0) FROM transfers WHERE from_user_id = $userId AND status IN ('PROCESSING','COMPLETED')"
      .query[Long]
      .run()
      .head

  def findByIdempotency(
      fromUserId: UUID,
      transferType: TransferType,
      key: String
  )(using DbCon): Option[Transfer] =
    None // Placeholder

  def listByUser(userId: UUID, limit: Int = 200)(using DbCon): List[Transfer] =
    List.empty // Placeholder

  def findById(transferId: UUID)(using DbCon): Option[Transfer] =
    None // Placeholder

  def get(transferId: UUID)(using DbCon): Transfer =
    findById(transferId).getOrElse(
      throw new RuntimeException(s"Transfer $transferId not found")
    )

  def updateStatus(transferId: UUID, status: TransferStatus)(using
      DbCon
  ): Unit =
    sql"UPDATE transfers SET status = ${status.toString} WHERE id = $transferId".update
      .run()

  def listFlagged(limit: Int)(using DbCon): List[Transfer] =
    List.empty // Placeholder
}

object RefreshTokenRepo {
  def insert(
      tokenId: UUID,
      userId: UUID,
      tokenHash: String,
      expiresAt: Instant
  )(using DbCon): Unit =
    sql"INSERT INTO refresh_tokens (token_id, user_id, token_hash, expires_at) VALUES ($tokenId, $userId, $tokenHash, $expiresAt)".update
      .run()

  def revoke(tokenId: UUID)(using DbCon): Unit =
    sql"UPDATE refresh_tokens SET revoked_at = now() WHERE token_id = $tokenId".update
      .run()

  def findValid(userId: UUID, tokenHash: String, now: Instant)(using
      DbCon
  ): Option[UUID] =
    sql"""SELECT token_id FROM refresh_tokens 
          WHERE user_id = $userId AND token_hash = $tokenHash AND revoked_at IS NULL AND expires_at > $now
          ORDER BY created_at DESC LIMIT 1""".query[UUID].run().headOption
}

object AuditRepo {
  def append(
      kind: String,
      userId: Option[UUID],
      correlationId: String,
      details: String
  )(using DbCon): Unit =
    sql"INSERT INTO audit_events (kind, user_id, correlation_id, details) VALUES ($kind, $userId, $correlationId, $details)".update
      .run()

  def listLatest(limit: Int)(using DbCon): List[AuditEvent] =
    List.empty // Placeholder
}

// Card repository
object CardRepo {
  def listByUser(userId: UUID)(using DbCon): List[Card] =
    List.empty // Placeholder

  def insert(card: Card)(using DbCon): Unit =
    sql"""INSERT INTO cards (id, user_id, kind, last4, status, delivery_status, created_at)
          VALUES (${card.id}, ${card.userId}, ${card.kind.toString}, ${card.last4}, ${card.status.toString}, ${card.deliveryStatus.toString}, ${card.createdAt})""".update
      .run()

  def updateDelivery(cardId: UUID, status: DeliveryStatus)(using DbCon): Unit =
    sql"UPDATE cards SET delivery_status = ${status.toString} WHERE id = $cardId".update
      .run()
}

// Family group repository
object FamilyRepo {
  def create(owner: UUID, members: Set[UUID])(using DbCon): FamilyGroup = {
    val id = UUID.randomUUID()
    val now = Instant.now()
    // Insert logic would go here
    FamilyGroup(id, owner, members, now)
  }

  def listByOwner(owner: UUID)(using DbCon): List[FamilyGroup] =
    List.empty

  def get(groupId: UUID)(using DbCon): Option[FamilyGroup] =
    None
}

// Loan repository
object LoanRepo {
  def insert(loan: Loan)(using DbCon): Unit = ()

  def listByUser(userId: UUID)(using DbCon): List[Loan] =
    List.empty
}

// Ledger repository for double-entry accounting
object LedgerRepo {
  def insert(
      debitAccountId: UUID,
      creditAccountId: UUID,
      amountMinor: Long,
      currency: String,
      description: String
  )(using DbCon): Unit = ()
}

// System accounts for clearing/settlement
object SystemAccounts {
  val TopupClearing: UUID =
    UUID.fromString("00000000-0000-0000-0000-000000000001")
  val CashClearing: UUID =
    UUID.fromString("00000000-0000-0000-0000-000000000002")
  val LoanFund: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
  val AchClearing: UUID =
    UUID.fromString("00000000-0000-0000-0000-000000000004")
}

// Paycheck enrollment repository
object PaycheckRepo {
  def upsert(enrollment: PaycheckEnrollment)(using DbCon): Unit = ()

  def get(userId: UUID)(using DbCon): Option[PaycheckEnrollment] =
    None
}

// Support ticket repository
object TicketRepo {
  def insert(ticket: SupportTicket)(using DbCon): Unit = ()

  def listByUser(userId: UUID)(using DbCon): List[SupportTicket] =
    List.empty
}
