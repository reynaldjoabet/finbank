package revenue.domain

import zio.json.*
import revenue.domain.ids.*

enum CaseType derives JsonCodec { case Audit, Investigation, CustomsHold }
enum CaseStatus derives JsonCodec { case Open, Assigned, InProgress, Closed }

final case class ComplianceCaseCreate(
  caseType: CaseType,
  taxpayerId: TaxpayerId,
  reason: String
) derives JsonCodec

final case class ComplianceCase(
  id: CaseId,
  caseType: CaseType,
  taxpayerId: TaxpayerId,
  reason: String,
  status: CaseStatus,
  assignedTo: Option[String],
  createdAtEpochMs: Long,
  updatedAtEpochMs: Long
) derives JsonCodec

final case class CaseAssign(
  assignedTo: String
) derives JsonCodec

final case class CaseStatusUpdate(
  status: CaseStatus
) derives JsonCodec

final case class CaseTaskCreate(
  title: String,
  dueEpochMs: Option[Long]
) derives JsonCodec

final case class CaseTask(
  id: String,
  caseId: CaseId,
  title: String,
  dueEpochMs: Option[Long],
  done: Boolean,
  createdAtEpochMs: Long
) derives JsonCodec

final case class CaseNoteCreate(
  note: String
) derives JsonCodec

final case class CaseNote(
  id: String,
  caseId: CaseId,
  note: String,
  createdAtEpochMs: Long,
  author: String
) derives JsonCodec

final case class QueueSummary(
  open: Int,
  assigned: Int,
  inProgress: Int
) derives JsonCodec