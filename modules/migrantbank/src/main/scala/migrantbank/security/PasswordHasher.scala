package migrantbank.security

import com.password4j.Password
import com.password4j.Argon2Function
import com.password4j.types.Argon2
import zio.*

trait PasswordHasher {
  def hash(raw: String): UIO[String]
  def verify(raw: String, hash: String): UIO[Boolean]
}

object PasswordHasher {

  val live: ZLayer[Any, Nothing, PasswordHasher] =
    ZLayer.succeed {
      new PasswordHasher {

        // Argon2 configuration:
        // m=64MB (65536), t=3 iterations, p=4 parallelism
        private val argon2 =
          Argon2Function.getInstance(65536, 3, 4, 32, Argon2.ID)

        override def hash(raw: String): UIO[String] =
          ZIO.attemptBlocking {
            Password.hash(raw).addRandomSalt().`with`(argon2).getResult
          }.orDie

        override def verify(raw: String, hash: String): UIO[Boolean] =
          ZIO
            .attemptBlocking {
              Password.check(raw, hash).`with`(argon2)
            }
            .catchAll { _ =>
              ZIO.succeed(false)
            }
      }
    }
}
