object SessionManager {
  
}

abstract class SessionManager {
  def createSession(userId: String): String
  def getUserId(sessionId: String): Option[String]
  def invalidateSession(sessionId: String): Unit
}
