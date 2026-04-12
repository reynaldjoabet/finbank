package unitypay
import sttp.client4._

object FlutterwaveClient {
  private val SecretKey = sys.env.getOrElse("FLW_SECRET_KEY", "your_test_key")
  private val BaseUrl = "https://api.flutterwave.com/v3"

  def initiateTransfer(payload: String) = {
    basicRequest
      .post(uri"$BaseUrl/transfers")
      .auth
      .bearer(SecretKey) // Automatically adds "Authorization: Bearer <key>"
      .contentType("application/json")
      .body(payload)
  }
}
