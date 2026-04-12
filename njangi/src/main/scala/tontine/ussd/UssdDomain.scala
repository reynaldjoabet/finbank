package tontine.ussd

import zio.json.*
import java.util.UUID
import java.time.Instant

/**
 * A USSD session represents a single unstructured supplementary service data
 * (USSD) conversation between a feature-phone user and finbank.
 *
 * USSD conversations are stateful but ephemeral — each request/response pair
 * is a "hop" in the same session.  Sessions expire after `ttlSeconds` of
 * inactivity (typically 180 s on African networks).
 */
final case class UssdSession(
    sessionId: String,
    phoneE164: String,
    currentMenu: UssdMenuId,
    context: Map[String, String], // transient form data collected during the session
    createdAt: Instant,
    lastActivityAt: Instant
)

opaque type UssdMenuId = String

/**
 * The complete USSD menu tree.
 *
 * Each node is identified by a `UssdMenuId`.  Navigation is driven by the
 * user's numeric input (e.g. "1", "2", "0" for back).
 */
object UssdMenuId {
  def apply(s: String): UssdMenuId = s
  def unapply(id: UssdMenuId): String = id

  given CanEqual[UssdMenuId, UssdMenuId] = CanEqual.derived
  given JsonEncoder[UssdMenuId] = JsonEncoder.string
  given JsonDecoder[UssdMenuId] = JsonDecoder.string

  /** Well-known menu IDs — allows compile-time references. */
  val Main:              UssdMenuId = UssdMenuId("MAIN")
  val Balance:           UssdMenuId = UssdMenuId("BALANCE")
  val MyCircles:         UssdMenuId = UssdMenuId("MY_CIRCLES")
  val Contribute:        UssdMenuId = UssdMenuId("CONTRIBUTE")
  val ContributeAmount:  UssdMenuId = UssdMenuId("CONTRIBUTE_AMOUNT")
  val ContributeConfirm: UssdMenuId = UssdMenuId("CONTRIBUTE_CONFIRM")
  val MyScore:           UssdMenuId = UssdMenuId("MY_SCORE")
  val Loan:              UssdMenuId = UssdMenuId("LOAN")
  val LoanAmount:        UssdMenuId = UssdMenuId("LOAN_AMOUNT")
  val LoanConfirm:       UssdMenuId = UssdMenuId("LOAN_CONFIRM")
  val Exit:              UssdMenuId = UssdMenuId("EXIT")
}

/**
 * A single menu screen rendered to the handset.
 *
 * @param text     The full text to display (max ~160 chars for standard USSD).
 * @param isFinal  If true, this is a terminal message — the session ends.
 */
final case class UssdResponse(
    text: String,
    isFinal: Boolean
)
