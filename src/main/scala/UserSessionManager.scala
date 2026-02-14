//final case class UserSessionManager()

abstract class UserSessionManager {
  def createSession(userId: String): String
  def getUserId(sessionId: String): Option[String]
  def invalidateSession(sessionId: String): Unit
}
