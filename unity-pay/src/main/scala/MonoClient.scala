package unitypay
import sttp.client4.*
object MonoClient {
  private val SecretKey = sys.env("MONO_SECRET_KEY")

  def fetchAccountData(accountId: String) = {
    basicRequest
      .get(uri"https://api.withmono.com/accounts/$accountId/data")
      .header(
        "mono-sec-key",
        SecretKey
      ) // Mono specifically looks for this header
      .header("Content-Type", "application/json")
  }
}
