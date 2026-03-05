package com.migrantbank.domain

sealed trait AppError extends Throwable {
  def message: String
  override def getMessage(): String = message
}
object AppError {
  final case class Validation(message: String) extends AppError
  final case class Unauthorized(message: String) extends AppError
  final case class Forbidden(message: String) extends AppError
  final case class NotFound(message: String) extends AppError
  final case class Conflict(message: String) extends AppError
  final case class ProviderUnavailable(message: String) extends AppError
  final case class RateLimited(message: String) extends AppError
  final case class Internal(message: String, cause: Option[Throwable] = None)
      extends AppError {
    override def getCause(): Throwable | Null = cause.orNull
  }

}
