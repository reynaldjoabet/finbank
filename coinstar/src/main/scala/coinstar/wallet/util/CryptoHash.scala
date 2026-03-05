package coinstar.wallet.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CryptoHash {
  def sha256Hex(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.getBytes(StandardCharsets.UTF_8))
    bytes.map("%02x".format(_)).mkString
  }
}
