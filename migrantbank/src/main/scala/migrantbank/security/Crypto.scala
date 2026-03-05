package com.migrantbank.security

import com.migrantbank.config.AppConfig
import com.migrantbank.domain.AppError
import zio.*

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

trait Crypto {
  def encryptUtf8(plain: String): IO[AppError, String]
  def decryptUtf8(cipherTextBase64: String): IO[AppError, String]
}
object Crypto {
  private val AesGcm = "AES/GCM/NoPadding"
  private val IvBytes = 12
  private val TagBits = 128

  val live: ZLayer[AppConfig, Nothing, Crypto] =
    ZLayer.fromFunction { (cfg: AppConfig) =>
      new Crypto {
        private val keyBytes =
          try Base64.getDecoder.decode(cfg.security.encryptionKeyBase64)
          catch case _: Throwable => Array.emptyByteArray

        private val key = new SecretKeySpec(keyBytes, "AES")
        private val rnd = new SecureRandom()

        private def failIfBadKey: IO[AppError, Unit] =
          if keyBytes.length != 32 then
            ZIO.fail(
              AppError.Internal(
                "Invalid encryption key: must be 32 bytes base64"
              )
            )
          else ZIO.unit

        override def encryptUtf8(plain: String): IO[AppError, String] =
          for {
            _ <- failIfBadKey
            iv = Array.ofDim[Byte](IvBytes)
            _ <- ZIO
              .attempt(rnd.nextBytes(iv))
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
            cipher <- ZIO
              .attempt {
                val c = Cipher.getInstance(AesGcm)
                c.init(
                  Cipher.ENCRYPT_MODE,
                  key,
                  new GCMParameterSpec(TagBits, iv)
                )
                c
              }
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
            ct <- ZIO
              .attempt(cipher.doFinal(plain.getBytes("UTF-8")))
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
            payload = iv ++ ct
          } yield Base64.getEncoder.encodeToString(payload)

        override def decryptUtf8(
            cipherTextBase64: String
        ): IO[AppError, String] =
          for {
            _ <- failIfBadKey
            raw <- ZIO
              .attempt(Base64.getDecoder.decode(cipherTextBase64))
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
            _ <- ZIO
              .fail(AppError.Validation("Ciphertext too short"))
              .when(raw.length <= IvBytes)
            iv = raw.take(IvBytes)
            ct = raw.drop(IvBytes)
            cipher <- ZIO
              .attempt {
                val c = Cipher.getInstance(AesGcm)
                c.init(
                  Cipher.DECRYPT_MODE,
                  key,
                  new GCMParameterSpec(TagBits, iv)
                )
                c
              }
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
            pt <- ZIO
              .attempt(cipher.doFinal(ct))
              .mapError(e => AppError.Internal("Crypto error", Some(e)))
          } yield new String(pt, "UTF-8")
      }

    }
}
