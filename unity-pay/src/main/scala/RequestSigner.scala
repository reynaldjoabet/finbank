import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import sttp.client4._

object RequestSigner {

  /** Generates an HMAC-SHA256 signature for a JSON payload. Useful for webhook
    * verification or high-security internal APIs.
    */
  def signBody(body: String, secret: String): String = {
    val hmacSha256 = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")
    hmacSha256.init(secretKey)

    val hash = hmacSha256.doFinal(body.getBytes("UTF-8"))
    Base64.getEncoder.encodeToString(hash)
  }

  // sttp integration
  def authenticatedRequest(payload: String, secret: String) = {
    val signature = signBody(payload, secret)

    basicRequest
      .post(uri"https://api.unitypay.africa/v1/settle")
      .header("X-Unity-Signature", signature) // Your custom security header
      .body(payload)
  }
}
