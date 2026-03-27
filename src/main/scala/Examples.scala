import com.github.plokhotnyuk.jsoniter_scala.macros.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import io.github.iltotore.iron.{:|, autoRefine, RefinedType, RefinedSubtype}
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.jsoniter.given
import pureconfig.{ConfigReader, ConfigSource}
import io.github.iltotore.iron.{pureconfig as ironPureconfig}
import ironPureconfig.given
object Examples {
  case class User(name: String :| Alphanumeric, age: Int :| Positive)
  given JsonValueCodec[User] = JsonCodecMaker.make

  // Encoding — refined type is just the base value, encodes normally
  writeToString(User("totore", 18)) // {"name":"totore","age":18}

  // Decoding — checks constraint, fails if invalid
  readFromString[User]("""{"name":"totore","age":18}""") // User("totore", 18)
  readFromString[User](
    """{"name":"totore","age":-18}"""
  ) // Error: "Should be strictly positive"
}

object ConfigExample {

  case class AppConfig(
      host: String :| Not[Empty],
      port: Int :| Positive
  ) derives ConfigReader

  // Load config from application.conf
  val config = ConfigSource.default.loadOrThrow[AppConfig]
// Newtypes
  type Username = Username.T
  object Username extends RefinedType[String, MinLength[5]]

  type Password = Password.T
  object Password extends RefinedSubtype[String, MinLength[5]]

// Works with plain refined types
  case class IronTypeConfig(
      username: String :| MinLength[5]
  ) derives ConfigReader

// Works with newtypes too
  case class NewTypeConfig(
      username: Username,
      password: Password
  ) derives ConfigReader
}
object ZioJsonExample {
  import zio.json.*
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.all.*
  import io.github.iltotore.iron.zioJson.given

  // With refined types directly
  case class User(name: String :| Alphanumeric, age: Int :| Positive)
  given JsonCodec[User] = DeriveJsonCodec.gen

  User("Iltotore", 18).toJson // {"name":"Iltotore","age":18}
  """{"name":"Iltotore","age":18}""".fromJson[User] // Right(User(Iltotore, 18))
  """{"name":"Iltotore","age":-18}"""
    .fromJson[User] // Left(.age(Should be greater than 0))
}
