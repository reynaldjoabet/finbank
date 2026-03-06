package revenue.service

import zio.*
import revenue.domain.*
import revenue.domain.ids.*
import revenue.repo.*
import java.util.Base64
import java.security.MessageDigest

trait DocumentService {
  def upload(
      req: DocumentUpload,
      principal: Principal
  ): IO[ApiError, DocumentMeta]
  def list(
      entityType: EntityType,
      entityId: String,
      principal: Principal
  ): IO[ApiError, List[DocumentMeta]]
}

object DocumentService {

  private def sha256Hex(bytes: Chunk[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes.toArray)
    md.digest().map(b => f"$b%02x").mkString
  }

  val live: URLayer[DocumentRepo & AuditService & Clock, DocumentService] =
    ZLayer.fromZIO {
      for {
        repo <- ZIO.service[DocumentRepo]
        audit <- ZIO.service[AuditService]
        clock <- ZIO.service[Clock]
      } yield new DocumentService {

        override def upload(
            req: DocumentUpload,
            principal: Principal
        ): IO[ApiError, DocumentMeta] = {
          for {
            now <- clock.instant.map(_.toEpochMilli)
            id <- Random.nextUUID.map(u => DocumentId(u.toString))

            bytes <- ZIO
              .attempt {
                val decoded = Base64.getDecoder.decode(req.base64)
                Chunk.fromArray(decoded)
              }
              .mapError(_ =>
                ApiError.BadRequest("Invalid base64 document payload")
              )

            meta = DocumentMeta(
              id = id,
              entityType = req.entityType,
              entityId = req.entityId,
              filename = req.filename,
              contentType = req.contentType,
              sizeBytes = bytes.size.toLong,
              sha256Hex = sha256Hex(bytes),
              createdAtEpochMs = now
            )

            saved <- repo.put(meta, bytes).mapError(ApiError.fromRepo)
            _ <- audit.record(
              principal,
              "DOCUMENT_UPLOADED",
              "Document",
              saved.id.value,
              s"${saved.entityType}:${saved.entityId} ${saved.filename}"
            )
          } yield saved
        }

        override def list(
            entityType: EntityType,
            entityId: String,
            principal: Principal
        ): IO[ApiError, List[DocumentMeta]] =
          repo.listByEntity(entityType, entityId).mapError(ApiError.fromRepo)
      }
    }
}
