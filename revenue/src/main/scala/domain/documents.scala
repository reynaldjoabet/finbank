package revenue.domain
import zio.json.*
import revenue.domain.ids.*

enum EntityType derives JsonCodec {
  case Return, Refund, Objection, Case
}

final case class DocumentUpload(
  entityType: EntityType,
  entityId: String,
  filename: String,
  contentType: String,
  base64: String
) derives JsonCodec

final case class DocumentMeta(
  id: DocumentId,
  entityType: EntityType,
  entityId: String,
  filename: String,
  contentType: String,
  sizeBytes: Long,
  sha256Hex: String,
  createdAtEpochMs: Long
) derives JsonCodec