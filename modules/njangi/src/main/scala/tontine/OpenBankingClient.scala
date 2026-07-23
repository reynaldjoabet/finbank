package tontine
import zio.*
import java.time.Instant
trait OpenBankingClient {
  def sweepToCircleAccount(
      circleBankAccountRef: String,
      amount: Money,
      reference: String
  ): IO[AppError.Bank, BankTxnId]
  def fetchTransactions(
      circleBankAccountRef: String,
      since: Instant
  ): IO[AppError.Bank, List[BankTransaction]]
}

final case class OpenBankingClientLive() extends OpenBankingClient {
  override def sweepToCircleAccount(
      circleBankAccountRef: String,
      amount: Money,
      reference: String
  ): IO[AppError.Bank, BankTxnId] = ???
  override def fetchTransactions(
      circleBankAccountRef: String,
      since: Instant
  ): IO[AppError.Bank, List[BankTransaction]] = ???
}

object OpenBankingClient {
  val layer: ULayer[OpenBankingClient] = ZLayer.succeed(OpenBankingClientLive())
}
