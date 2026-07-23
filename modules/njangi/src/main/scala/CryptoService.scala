import zio._
import java.security._
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher

object CryptoService {

  // 1. Hashing Algorithm (SHA-256)
  def sha256(input: String): UIO[String] = ZIO.succeed {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8))
    String.format("%064x", new java.math.BigInteger(1, hashBytes))
  }

  // 2. Digital Signature Verification (ECDSA)
  // Uses the secp256k1 curve (common in Bitcoin/Ethereum)
  def verifySignature(
      publicKeyBase64: String,
      data: String,
      signatureBase64: String
  ): Task[Boolean] = ZIO.attempt {
    val keyBytes = Base64.getDecoder.decode(publicKeyBase64)
    val sigBytes = Base64.getDecoder.decode(signatureBase64)

    val keyFactory = KeyFactory.getInstance("EC")
    val publicKey = keyFactory.generatePublic(
      new java.security.spec.X509EncodedKeySpec(keyBytes)
    )

    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(data.getBytes(StandardCharsets.UTF_8))
    verifier.verify(sigBytes)
  }
}

object CryptoApp extends ZIOAppDefault {
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  val ciph = Cipher.getInstance("ChaCha20-Poly1305")
  def run = for {
    _ <- Console.printLine("--- Blockchain Crypto Demo ---")

    // Hashing a block's data
    data = "transaction: John -> Alice: 10 BTC"
    hash <- CryptoService.sha256(data)
    _ <- Console.printLine(s"SHA-256 Hash: $hash")

    // In a real app, you'd verify a signature here.
    // For now, let's just confirm the hash looks correct.
    _ <- ZIO.when(hash.startsWith("0000"))(
      Console.printLine("Found a valid Proof of Work hash!")
    )
  } yield ()
}
