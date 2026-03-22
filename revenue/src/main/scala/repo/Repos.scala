package revenue.repo

import zio.*
import revenue.domain.*
import revenue.domain.ids.*

sealed trait RepoError extends Throwable
object RepoError {
  final case class NotFound(entity: String, id: String)
      extends Exception(s"$entity not found: $id")
      with RepoError
  final case class Conflict(message: String)
      extends Exception(message)
      with RepoError
  final case class Storage(message: String)
      extends Exception(message)
      with RepoError
}

trait TaxpayerRepo {
  def create(
      reg: TaxpayerRegistration,
      nowMs: Long,
      id: TaxpayerId,
      tan: String
  ): IO[RepoError, Taxpayer]
  def get(id: TaxpayerId): IO[RepoError, Option[Taxpayer]]
  def list(): IO[RepoError, List[Taxpayer]]
}

trait ReturnRepo {
  def createDraft(
      req: ReturnDraftCreate,
      nowMs: Long,
      id: ReturnId
  ): IO[RepoError, TaxReturn]
  def updateDraft(
      id: ReturnId,
      payload: zio.json.ast.Json,
      nowMs: Long
  ): IO[RepoError, TaxReturn]
  def setStatus(
      id: ReturnId,
      status: ReturnStatus,
      nowMs: Long,
      submittedAt: Option[Long]
  ): IO[RepoError, TaxReturn]
  def amend(
      id: ReturnId,
      nowMs: Long,
      newId: ReturnId
  ): IO[RepoError, TaxReturn]
  def get(id: ReturnId): IO[RepoError, Option[TaxReturn]]
  def listByTaxpayer(taxpayerId: TaxpayerId): IO[RepoError, List[TaxReturn]]
}

trait RiskRuleRepo {
  def list(): IO[RepoError, List[RiskRule]]
  def create(rule: RiskRule, nowMs: Long): IO[RepoError, RiskRule]
  def setEnabled(
      id: RiskRuleId,
      enabled: Boolean,
      nowMs: Long
  ): IO[RepoError, RiskRule]
}

trait AssessmentRepo {
  def create(a: Assessment, liabilities: List[Liability]): IO[RepoError, Unit]
  def get(id: AssessmentId): IO[RepoError, Option[Assessment]]
  def listLiabilities(
      taxpayerId: TaxpayerId,
      status: Option[LiabilityStatus]
  ): IO[RepoError, List[Liability]]
  def getLiability(id: LiabilityId): IO[RepoError, Option[Liability]]
  def updateLiability(liability: Liability): IO[RepoError, Unit]
}

trait PaymentRepo {
  def create(p: Payment): IO[RepoError, Payment]
  def get(id: PaymentId): IO[RepoError, Option[Payment]]
  def listByTaxpayer(taxpayerId: TaxpayerId): IO[RepoError, List[Payment]]
  def update(p: Payment): IO[RepoError, Unit]

  def upsertReceipt(r: Receipt): IO[RepoError, Receipt]
  def getReceiptByPayment(paymentId: PaymentId): IO[RepoError, Option[Receipt]]
}

trait RefundRepo {
  def create(claim: RefundClaim): IO[RepoError, RefundClaim]
  def get(id: RefundId): IO[RepoError, Option[RefundClaim]]
  def listByTaxpayer(taxpayerId: TaxpayerId): IO[RepoError, List[RefundClaim]]
  def update(claim: RefundClaim): IO[RepoError, RefundClaim]
}

trait ObjectionRepo {
  def create(o: Objection): IO[RepoError, Objection]
  def get(id: ObjectionId): IO[RepoError, Option[Objection]]
  def update(o: Objection): IO[RepoError, Objection]
}

trait CaseRepo {
  def create(c: ComplianceCase): IO[RepoError, ComplianceCase]
  def get(id: CaseId): IO[RepoError, Option[ComplianceCase]]
  def list(status: Option[CaseStatus]): IO[RepoError, List[ComplianceCase]]
  def update(c: ComplianceCase): IO[RepoError, ComplianceCase]

  def addTask(t: CaseTask): IO[RepoError, CaseTask]
  def addNote(n: CaseNote): IO[RepoError, CaseNote]
  def listTasks(caseId: CaseId): IO[RepoError, List[CaseTask]]
  def listNotes(caseId: CaseId): IO[RepoError, List[CaseNote]]
}

trait DocumentRepo {
  def put(meta: DocumentMeta, bytes: Chunk[Byte]): IO[RepoError, DocumentMeta]
  def getMeta(id: DocumentId): IO[RepoError, Option[DocumentMeta]]
  def getBytes(id: DocumentId): IO[RepoError, Option[Chunk[Byte]]]
  def listByEntity(
      entityType: EntityType,
      entityId: String
  ): IO[RepoError, List[DocumentMeta]]
}

trait AuditRepo {
  def append(evt: AuditEvent): UIO[Unit]
  def latest(limit: Int): UIO[List[AuditEvent]]
}

trait UserRepo {
  def findByUsername(username: String): IO[RepoError, Option[User]]
  def get(id: UserId): IO[RepoError, Option[User]]
}

trait RefreshTokenRepo {
  def create(r: RefreshTokenRecord): IO[RepoError, RefreshTokenRecord]
  def findByToken(token: String): IO[RepoError, Option[RefreshTokenRecord]]
  def revoke(id: RefreshTokenId): IO[RepoError, Unit]
}
