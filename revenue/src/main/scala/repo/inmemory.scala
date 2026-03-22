package revenue.repo

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import zio.json.ast.Json

object inmemory {

  final class InMemoryTaxpayerRepo private (
      data: Ref[Map[TaxpayerId, Taxpayer]]
  ) extends TaxpayerRepo {
    override def create(
        reg: TaxpayerRegistration,
        nowMs: Long,
        id: TaxpayerId,
        tan: String
    ): IO[RepoError, Taxpayer] = {
      val tp = Taxpayer(
        id,
        tan,
        reg.kind,
        reg.legalName,
        reg.identifiers,
        "ACTIVE",
        nowMs
      )
      data.update(_ + (id -> tp)).as(tp)
    }
    override def get(id: TaxpayerId): IO[RepoError, Option[Taxpayer]] =
      data.get.map(_.get(id))
    override def list(): IO[RepoError, List[Taxpayer]] =
      data.get.map(_.values.toList)
  }
  object InMemoryTaxpayerRepo {
    val layer: ULayer[TaxpayerRepo] =
      ZLayer.fromZIO(
        Ref
          .make(Map.empty[TaxpayerId, Taxpayer])
          .map(new InMemoryTaxpayerRepo(_))
      )
  }

  final class InMemoryReturnRepo private (data: Ref[Map[ReturnId, TaxReturn]])
      extends ReturnRepo {

    override def createDraft(
        req: ReturnDraftCreate,
        nowMs: Long,
        id: ReturnId
    ): IO[RepoError, TaxReturn] = {
      val tr = TaxReturn(
        id = id,
        taxpayerId = req.taxpayerId,
        taxType = req.taxType,
        period = req.period,
        payload = Json.Obj(),
        status = ReturnStatus.Draft,
        version = 1,
        amendedFrom = None,
        createdAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
        submittedAtEpochMs = None
      )
      data.update(_ + (id -> tr)).as(tr)
    }

    override def updateDraft(
        id: ReturnId,
        payload: Json,
        nowMs: Long
    ): IO[RepoError, TaxReturn] = {
      data.modify { m =>
        m.get(id) match {
          case None => (ZIO.fail(RepoError.NotFound("Return", id.value)), m)
          case Some(prev) =>
            val next = prev.copy(payload = payload, updatedAtEpochMs = nowMs)
            (ZIO.succeed(next), m.updated(id, next))
        }
      }.flatten
    }

    override def setStatus(
        id: ReturnId,
        status: ReturnStatus,
        nowMs: Long,
        submittedAt: Option[Long]
    ): IO[RepoError, TaxReturn] = {
      data.modify { m =>
        m.get(id) match {
          case None => (ZIO.fail(RepoError.NotFound("Return", id.value)), m)
          case Some(prev) =>
            val next = prev.copy(
              status = status,
              updatedAtEpochMs = nowMs,
              submittedAtEpochMs = submittedAt.orElse(prev.submittedAtEpochMs)
            )
            (ZIO.succeed(next), m.updated(id, next))
        }
      }.flatten
    }

    override def amend(
        id: ReturnId,
        nowMs: Long,
        newId: ReturnId
    ): IO[RepoError, TaxReturn] = {
      data.modify { m =>
        m.get(id) match {
          case None => (ZIO.fail(RepoError.NotFound("Return", id.value)), m)
          case Some(prev) =>
            val next = prev.copy(
              id = newId,
              status = ReturnStatus.Draft,
              version = prev.version + 1,
              amendedFrom = Some(prev.id),
              createdAtEpochMs = nowMs,
              updatedAtEpochMs = nowMs,
              submittedAtEpochMs = None
            )
            (ZIO.succeed(next), m.updated(newId, next))
        }
      }.flatten
    }

    override def get(id: ReturnId): IO[RepoError, Option[TaxReturn]] =
      data.get.map(_.get(id))

    override def listByTaxpayer(
        taxpayerId: TaxpayerId
    ): IO[RepoError, List[TaxReturn]] =
      data.get.map(
        _.values
          .filter(_.taxpayerId == taxpayerId)
          .toList
          .sortBy(_.updatedAtEpochMs)
          .reverse
      )
  }
  object InMemoryReturnRepo {
    val layer: ULayer[ReturnRepo] =
      ZLayer.fromZIO(
        Ref.make(Map.empty[ReturnId, TaxReturn]).map(new InMemoryReturnRepo(_))
      )
  }

  final class InMemoryRiskRuleRepo private (
      data: Ref[Map[RiskRuleId, RiskRule]]
  ) extends RiskRuleRepo {
    override def list(): IO[RepoError, List[RiskRule]] =
      data.get.map(_.values.toList)
    override def create(
        rule: RiskRule,
        nowMs: Long
    ): IO[RepoError, RiskRule] = {
      val r = rule.copy(createdAtEpochMs = nowMs, updatedAtEpochMs = nowMs)
      data.update(_ + (r.id -> r)).as(r)
    }
    override def setEnabled(
        id: RiskRuleId,
        enabled: Boolean,
        nowMs: Long
    ): IO[RepoError, RiskRule] = {
      data.modify { m =>
        m.get(id) match {
          case None => (ZIO.fail(RepoError.NotFound("RiskRule", id.value)), m)
          case Some(prev) =>
            val next = prev.copy(enabled = enabled, updatedAtEpochMs = nowMs)
            (ZIO.succeed(next), m.updated(id, next))
        }
      }.flatten
    }
  }
  object InMemoryRiskRuleRepo {
    val layer: ULayer[RiskRuleRepo] =
      ZLayer.fromZIO(
        Ref
          .make(Map.empty[RiskRuleId, RiskRule])
          .map(new InMemoryRiskRuleRepo(_))
      )
  }

  final class InMemoryAssessmentRepo private (
      assessments: Ref[Map[AssessmentId, Assessment]],
      liabilities: Ref[Map[LiabilityId, Liability]]
  ) extends AssessmentRepo {

    override def create(
        a: Assessment,
        ls: List[Liability]
    ): IO[RepoError, Unit] =
      assessments.update(_ + (a.id -> a)) *>
        liabilities.update { m =>
          ls.foldLeft(m) { case (acc, l) => acc.updated(l.id, l) }
        }.unit

    override def get(id: AssessmentId): IO[RepoError, Option[Assessment]] =
      assessments.get.map(_.get(id))

    override def listLiabilities(
        taxpayerId: TaxpayerId,
        status: Option[LiabilityStatus]
    ): IO[RepoError, List[Liability]] =
      liabilities.get.map { m =>
        val base = m.values.filter(_.taxpayerId == taxpayerId)
        status match {
          case None    => base.toList
          case Some(s) => base.filter(_.status == s).toList
        }
      }

    override def getLiability(
        id: LiabilityId
    ): IO[RepoError, Option[Liability]] =
      liabilities.get.map(_.get(id))

    override def updateLiability(liability: Liability): IO[RepoError, Unit] =
      liabilities.update(_ + (liability.id -> liability)).unit
  }
  object InMemoryAssessmentRepo {
    val layer: ULayer[AssessmentRepo] =
      ZLayer.fromZIO {
        for {
          a <- Ref.make(Map.empty[AssessmentId, Assessment])
          l <- Ref.make(Map.empty[LiabilityId, Liability])
        } yield new InMemoryAssessmentRepo(a, l)
      }
  }

  final class InMemoryPaymentRepo private (
      payments: Ref[Map[PaymentId, Payment]],
      receipts: Ref[Map[PaymentId, Receipt]]
  ) extends PaymentRepo {

    override def create(p: Payment): IO[RepoError, Payment] =
      payments.update(_ + (p.id -> p)).as(p)

    override def get(id: PaymentId): IO[RepoError, Option[Payment]] =
      payments.get.map(_.get(id))

    override def listByTaxpayer(
        taxpayerId: TaxpayerId
    ): IO[RepoError, List[Payment]] =
      payments.get.map(
        _.values
          .filter(_.taxpayerId == taxpayerId)
          .toList
          .sortBy(_.createdAtEpochMs)
          .reverse
      )

    override def update(p: Payment): IO[RepoError, Unit] =
      payments.update(_ + (p.id -> p)).unit

    override def upsertReceipt(r: Receipt): IO[RepoError, Receipt] =
      receipts.update(_ + (r.paymentId -> r)).as(r)

    override def getReceiptByPayment(
        paymentId: PaymentId
    ): IO[RepoError, Option[Receipt]] =
      receipts.get.map(_.get(paymentId))
  }
  object InMemoryPaymentRepo {
    val layer: ULayer[PaymentRepo] =
      ZLayer.fromZIO {
        for {
          p <- Ref.make(Map.empty[PaymentId, Payment])
          r <- Ref.make(Map.empty[PaymentId, Receipt])
        } yield new InMemoryPaymentRepo(p, r)
      }
  }

  final class InMemoryRefundRepo private (data: Ref[Map[RefundId, RefundClaim]])
      extends RefundRepo {
    override def create(claim: RefundClaim): IO[RepoError, RefundClaim] =
      data.update(_ + (claim.id -> claim)).as(claim)
    override def get(id: RefundId): IO[RepoError, Option[RefundClaim]] =
      data.get.map(_.get(id))
    override def listByTaxpayer(
        taxpayerId: TaxpayerId
    ): IO[RepoError, List[RefundClaim]] =
      data.get.map(
        _.values
          .filter(_.taxpayerId == taxpayerId)
          .toList
          .sortBy(_.updatedAtEpochMs)
          .reverse
      )
    override def update(claim: RefundClaim): IO[RepoError, RefundClaim] =
      data.update(_ + (claim.id -> claim)).as(claim)
  }
  object InMemoryRefundRepo {
    val layer: ULayer[RefundRepo] =
      ZLayer.fromZIO(
        Ref
          .make(Map.empty[RefundId, RefundClaim])
          .map(new InMemoryRefundRepo(_))
      )
  }

  final class InMemoryObjectionRepo private (
      data: Ref[Map[ObjectionId, Objection]]
  ) extends ObjectionRepo {
    override def create(o: Objection): IO[RepoError, Objection] =
      data.update(_ + (o.id -> o)).as(o)
    override def get(id: ObjectionId): IO[RepoError, Option[Objection]] =
      data.get.map(_.get(id))
    override def update(o: Objection): IO[RepoError, Objection] =
      data.update(_ + (o.id -> o)).as(o)
  }
  object InMemoryObjectionRepo {
    val layer: ULayer[ObjectionRepo] =
      ZLayer.fromZIO(
        Ref
          .make(Map.empty[ObjectionId, Objection])
          .map(new InMemoryObjectionRepo(_))
      )
  }

  final class InMemoryCaseRepo private (
      cases: Ref[Map[CaseId, ComplianceCase]],
      tasks: Ref[Map[CaseId, Vector[CaseTask]]],
      notes: Ref[Map[CaseId, Vector[CaseNote]]]
  ) extends CaseRepo {

    override def create(c: ComplianceCase): IO[RepoError, ComplianceCase] =
      cases.update(_ + (c.id -> c)).as(c)

    override def get(id: CaseId): IO[RepoError, Option[ComplianceCase]] =
      cases.get.map(_.get(id))

    override def list(
        status: Option[CaseStatus]
    ): IO[RepoError, List[ComplianceCase]] =
      cases.get.map { m =>
        val xs = m.values
        status match {
          case None    => xs.toList
          case Some(s) => xs.filter(_.status == s).toList
        }
      }

    override def update(c: ComplianceCase): IO[RepoError, ComplianceCase] =
      cases.update(_ + (c.id -> c)).as(c)

    override def addTask(t: CaseTask): IO[RepoError, CaseTask] =
      tasks
        .update { m =>
          val v = m.getOrElse(t.caseId, Vector.empty) :+ t
          m.updated(t.caseId, v)
        }
        .as(t)

    override def addNote(n: CaseNote): IO[RepoError, CaseNote] =
      notes
        .update { m =>
          val v = m.getOrElse(n.caseId, Vector.empty) :+ n
          m.updated(n.caseId, v)
        }
        .as(n)

    override def listTasks(caseId: CaseId): IO[RepoError, List[CaseTask]] =
      tasks.get.map(_.getOrElse(caseId, Vector.empty).toList)

    override def listNotes(caseId: CaseId): IO[RepoError, List[CaseNote]] =
      notes.get.map(_.getOrElse(caseId, Vector.empty).toList)
  }
  object InMemoryCaseRepo {
    val layer: ULayer[CaseRepo] =
      ZLayer.fromZIO {
        for {
          c <- Ref.make(Map.empty[CaseId, ComplianceCase])
          t <- Ref.make(Map.empty[CaseId, Vector[CaseTask]])
          n <- Ref.make(Map.empty[CaseId, Vector[CaseNote]])
        } yield new InMemoryCaseRepo(c, t, n)
      }
  }

  final class InMemoryDocumentRepo private (
      metas: Ref[Map[DocumentId, DocumentMeta]],
      bytes: Ref[Map[DocumentId, Chunk[Byte]]]
  ) extends DocumentRepo {

    override def put(
        meta: DocumentMeta,
        data: Chunk[Byte]
    ): IO[RepoError, DocumentMeta] =
      metas.update(_ + (meta.id -> meta)) *>
        bytes.update(_ + (meta.id -> data)).as(meta)

    override def getMeta(id: DocumentId): IO[RepoError, Option[DocumentMeta]] =
      metas.get.map(_.get(id))
    override def getBytes(id: DocumentId): IO[RepoError, Option[Chunk[Byte]]] =
      bytes.get.map(_.get(id))

    override def listByEntity(
        entityType: EntityType,
        entityId: String
    ): IO[RepoError, List[DocumentMeta]] =
      metas.get.map(
        _.values
          .filter(m => m.entityType == entityType && m.entityId == entityId)
          .toList
      )
  }
  object InMemoryDocumentRepo {
    val layer: ULayer[DocumentRepo] =
      ZLayer.fromZIO {
        for {
          m <- Ref.make(Map.empty[DocumentId, DocumentMeta])
          b <- Ref.make(Map.empty[DocumentId, Chunk[Byte]])
        } yield new InMemoryDocumentRepo(m, b)
      }
  }

  final class InMemoryAuditRepo private (events: Ref[Vector[AuditEvent]])
      extends AuditRepo {
    override def append(evt: AuditEvent): UIO[Unit] =
      events.update(_ :+ evt).unit
    override def latest(limit: Int): UIO[List[AuditEvent]] =
      events.get.map(_.takeRight(limit).toList.reverse)
  }
  object InMemoryAuditRepo {
    val layer: ULayer[AuditRepo] =
      ZLayer.fromZIO(
        Ref.make(Vector.empty[AuditEvent]).map(new InMemoryAuditRepo(_))
      )
  }

  final class InMemoryUserRepo private (users: Map[String, User])
      extends UserRepo {
    override def findByUsername(username: String): IO[RepoError, Option[User]] =
      ZIO.succeed(users.get(username))
    override def get(id: UserId): IO[RepoError, Option[User]] =
      ZIO.succeed(users.values.find(_.id == id))
  }
  object InMemoryUserRepo {
    val layer: ULayer[UserRepo] = {
      // These hashes are placeholders; AuthService has a hasher utility for generating real ones.
      val admin =
        User(UserId("u-admin"), "admin", "HASH_ME", Set(Role.Admin), None)
      val officer =
        User(UserId("u-officer"), "officer", "HASH_ME", Set(Role.Officer), None)
      val taxpayer = User(
        UserId("u-taxpayer"),
        "taxpayer",
        "HASH_ME",
        Set(Role.Taxpayer),
        None
      )
      ZLayer.succeed(
        new InMemoryUserRepo(
          Map(
            admin.username -> admin,
            officer.username -> officer,
            taxpayer.username -> taxpayer
          )
        )
      )
    }
  }

  final class InMemoryRefreshTokenRepo private (
      data: Ref[Map[RefreshTokenId, RefreshTokenRecord]]
  ) extends RefreshTokenRepo {
    override def create(
        r: RefreshTokenRecord
    ): IO[RepoError, RefreshTokenRecord] =
      data.update(_ + (r.id -> r)).as(r)

    override def findByToken(
        token: String
    ): IO[RepoError, Option[RefreshTokenRecord]] =
      data.get.map(_.values.find(_.token == token))

    override def revoke(id: RefreshTokenId): IO[RepoError, Unit] =
      data.update { m =>
        m.get(id) match {
          case None       => m
          case Some(prev) => m.updated(id, prev.copy(revoked = true))
        }
      }.unit
  }
  object InMemoryRefreshTokenRepo {
    val layer: ULayer[RefreshTokenRepo] =
      ZLayer.fromZIO(
        Ref
          .make(Map.empty[RefreshTokenId, RefreshTokenRecord])
          .map(new InMemoryRefreshTokenRepo(_))
      )
  }
}
