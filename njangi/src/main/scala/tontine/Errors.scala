package tontine

enum AppError(msg: String) extends Exception(msg) {
  case NotFound(msg: String) extends AppError(msg)
  case Validation(msg: String) extends AppError(msg)
  case Payment(msg: String) extends AppError(msg)
  case Bank(msg: String) extends AppError(msg)

  def message: String = this match {
    case NotFound(msg)   => msg
    case Validation(msg) => msg
    case Payment(msg)    => msg
    case Bank(msg)       => msg
  }

}
