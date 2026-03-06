package revenue.service

import revenue.repo.RepoError

sealed trait ApiError extends Throwable { def message: String }
object ApiError {
  final case class BadRequest(message: String)
      extends Exception(message)
      with ApiError
  final case class Unauthorized(message: String)
      extends Exception(message)
      with ApiError
  final case class Forbidden(message: String)
      extends Exception(message)
      with ApiError
  final case class NotFound(message: String)
      extends Exception(message)
      with ApiError
  final case class Conflict(message: String)
      extends Exception(message)
      with ApiError
  final case class Internal(message: String)
      extends Exception(message)
      with ApiError

  def fromRepo(e: RepoError): ApiError =
    e match {
      case RepoError.NotFound(entity, id) => NotFound(s"$entity not found: $id")
      case RepoError.Conflict(msg)        => Conflict(msg)
      case RepoError.Storage(msg)         => Internal(msg)
    }
}
