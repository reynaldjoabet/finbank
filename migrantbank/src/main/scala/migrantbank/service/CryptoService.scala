import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import java.security.SecureRandom
import java.util.Base64

object CryptoService {

  // Configuration for ChaCha20-Poly1305 AEAD
  private val Algorithm = "ChaCha20-Poly1305"
  private val NonceSize = 12 // 96 bits as per RFC 7539
  private val KeySize = 256

  def encrypt(
      plaintext: String,
      key: SecretKey,
      associatedData: String
  ): String = {
    val cipher = Cipher.getInstance(Algorithm)

    // 1. Generate a random Nonce (Never reuse this with the same key!)
    val nonce = new Array[Byte](NonceSize)
    SecureRandom().nextBytes(nonce)
    val ivSpec = new IvParameterSpec(nonce)

    // 2. Initialize
    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)

    // 3. Add the "Associated Data" (The 'AD' in AEAD)
    // This is public but protected from tampering.
    cipher.updateAAD(associatedData.getBytes("UTF-8"))

    // 4. Encrypt the secret message
    val ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"))

    // 5. Concatenate [Nonce + Ciphertext] for storage
    // The Tag is automatically appended to the ciphertext by Java
    val combined = nonce ++ ciphertext
    Base64.getEncoder.encodeToString(combined)
  }

  def decrypt(
      combinedEncoded: String,
      key: SecretKey,
      associatedData: String
  ): String = {
    val combined = Base64.getDecoder.decode(combinedEncoded)

    // 1. Split the Nonce from the Ciphertext
    val (nonce, ciphertext) = combined.splitAt(NonceSize)
    val ivSpec = new IvParameterSpec(nonce)

    val cipher = Cipher.getInstance(Algorithm)
    cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

    // 2. Provide the same AD. If this doesn't match what was used
    // during encryption, doFinal() will throw an AEADBadTagException.
    cipher.updateAAD(associatedData.getBytes("UTF-8"))

    val decrypted = cipher.doFinal(ciphertext)
    new String(decrypted, "UTF-8")
  }
}
