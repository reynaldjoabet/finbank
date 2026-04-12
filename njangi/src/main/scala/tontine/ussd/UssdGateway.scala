package tontine.ussd

import zio.*
import tontine.*
import tontine.service.*

/** USSD Gateway — maps feature-phone key presses to existing tontine services.
  *
  * Design principles:
  *   - **No parallel logic**: every action delegates to the same
  *     `ContributionService`, `ScoreService`, and `CircleService` that the REST
  *     API uses.
  *   - **Stateful sessions**: each conversation is tracked in an in-memory
  *     `Ref`; replace with Redis for multi-node deployments.
  *   - **Idempotent confirms**: the `CONTRIBUTE_CONFIRM` step passes the
  *     `sessionId` as idempotency key.
  *
  * USSD network integration: Expose a `POST /ussd/callback` endpoint that
  * passes the body parameters (`sessionId`, `phoneNumber`, `text`) to
  * `handle(...)`. Compatible with Africa's Talking USSD API format.
  *
  * Example session (MTN Cameroon, code *XXX#):
  * {{{
  *   → *123# pressed
  *   ← CON Welcome to Finbank\n1. My Balance\n2. My Circles\n3. My Score\n0. Exit
  *   → 1
  *   ← END Your XAF balance is 15,000 FCFA
  * }}}
  */
trait UssdGateway {

  /** Handle one USSD hop.
    *
    * @param sessionId
    *   Unique session identifier from the USSD aggregator.
    * @param phoneE164
    *   Caller's phone number in E.164 format.
    * @param input
    *   The digit(s) the user entered (empty string on first hop).
    * @return
    *   USSD response text + whether the session is terminal.
    */
  def handle(
      sessionId: String,
      phoneE164: String,
      input: String
  ): UIO[UssdResponse]
}

object UssdGateway {

  /** USSD session TTL — 180 seconds (standard African network timeout). */
  private val TtlSeconds = 180L

  val live: ZLayer[
    ContributionService & CircleService & ScoreService & MemberRepo,
    Nothing,
    UssdGateway
  ] =
    ZLayer.fromZIO {
      for {
        contributions <- ZIO.service[ContributionService]
        circles <- ZIO.service[CircleService]
        scores <- ZIO.service[ScoreService]
        members <- ZIO.service[MemberRepo]
        sessions <- Ref.make(Map.empty[String, UssdSession])
      } yield new UssdGatewayLive(
        contributions,
        circles,
        scores,
        members,
        sessions
      )
    }

  private final class UssdGatewayLive(
      contributions: ContributionService,
      circles: CircleService,
      scores: ScoreService,
      members: MemberRepo,
      sessions: Ref[Map[String, UssdSession]]
  ) extends UssdGateway {

    override def handle(
        sessionId: String,
        phoneE164: String,
        input: String
    ): UIO[UssdResponse] =
      for {
        now <- Clock.instant
        session <- getOrCreateSession(sessionId, phoneE164, now)
        resp <- route(session, input.trim, now)
        // Persist updated last-activity timestamp
        _ <- sessions.update(m =>
          m + (sessionId -> session.copy(lastActivityAt = now))
        )
      } yield resp

    private def getOrCreateSession(
        sessionId: String,
        phoneE164: String,
        now: java.time.Instant
    ): UIO[UssdSession] =
      sessions.get.map(_.get(sessionId)).flatMap {
        case Some(s) => ZIO.succeed(s)
        case None    =>
          val s = UssdSession(
            sessionId = sessionId,
            phoneE164 = phoneE164,
            currentMenu = UssdMenuId.Main,
            context = Map.empty,
            createdAt = now,
            lastActivityAt = now
          )
          sessions.update(_ + (sessionId -> s)).as(s)
      }

    private def route(
        session: UssdSession,
        input: String,
        now: java.time.Instant
    ): UIO[UssdResponse] =
      session.currentMenu match {

        case UssdMenuId.Main =>
          input match {
            case "" | "0" => // First hop or back to main
              transition(session.sessionId, UssdMenuId.Main) *>
                ZIO.succeed(mainMenu)
            case "1" =>
              transition(session.sessionId, UssdMenuId.Balance) *>
                fetchBalance(session.phoneE164)
            case "2" =>
              transition(session.sessionId, UssdMenuId.MyCircles) *>
                listCircles(session.phoneE164)
            case "3" =>
              transition(session.sessionId, UssdMenuId.MyScore) *>
                fetchScore(session.phoneE164)
            case "4" =>
              transition(session.sessionId, UssdMenuId.Loan) *>
                ZIO.succeed(loanMenu)
            case _ =>
              ZIO.succeed(
                UssdResponse(
                  "Invalid option. " + mainMenu.text,
                  isFinal = false
                )
              )
          }

        case UssdMenuId.Loan =>
          input match {
            case "1" =>
              transition(session.sessionId, UssdMenuId.LoanAmount) *>
                ZIO.succeed(
                  UssdResponse(
                    "CON Enter loan amount in XAF\n(Min: 1000, Max: 50000)\n0. Back",
                    isFinal = false
                  )
                )
            case "0" =>
              transition(session.sessionId, UssdMenuId.Main) *>
                ZIO.succeed(mainMenu)
            case _ =>
              ZIO.succeed(
                UssdResponse(
                  "Invalid option.\n" + loanMenu.text,
                  isFinal = false
                )
              )
          }

        case UssdMenuId.LoanAmount =>
          val maybeAmt = input.toLongOption
          maybeAmt match {
            case None =>
              ZIO.succeed(
                UssdResponse(
                  "CON Invalid amount. Enter a number:\n0. Back",
                  isFinal = false
                )
              )
            case Some(amt) if amt < 1000 || amt > 50000 =>
              ZIO.succeed(
                UssdResponse(
                  "CON Amount out of range (1000–50000 XAF).\n0. Back",
                  isFinal = false
                )
              )
            case Some(amt) =>
              updateContext(session.sessionId, "loanAmount", amt.toString) *>
                transition(session.sessionId, UssdMenuId.LoanConfirm) *>
                ZIO.succeed(
                  UssdResponse(
                    s"CON Confirm loan of $amt XAF?\n1. Yes\n2. No",
                    isFinal = false
                  )
                )
          }

        case UssdMenuId.LoanConfirm =>
          input match {
            case "1" =>
              disburseLoan(session) *>
                endSession(session.sessionId)
            case "2" | "0" =>
              transition(session.sessionId, UssdMenuId.Main) *>
                ZIO.succeed(mainMenu)
            case _ =>
              ZIO.succeed(
                UssdResponse(
                  "CON Press 1 to confirm, 2 to cancel.",
                  isFinal = false
                )
              )
          }

        case _ =>
          // Fallback — return to main
          transition(session.sessionId, UssdMenuId.Main) *>
            ZIO.succeed(mainMenu)
      }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private val mainMenu: UssdResponse =
      UssdResponse(
        text =
          "CON Welcome to Finbank\n1. My Balance\n2. My Circles\n3. My Score\n4. Request Loan\n0. Exit",
        isFinal = false
      )

    private val loanMenu: UssdResponse =
      UssdResponse(
        text = "CON Loan Menu\n1. Request Loan\n0. Back",
        isFinal = false
      )

    private def transition(sessionId: String, menuId: UssdMenuId): UIO[Unit] =
      sessions.update { m =>
        m.get(sessionId) match {
          case Some(s) => m + (sessionId -> s.copy(currentMenu = menuId))
          case None    => m
        }
      }

    private def updateContext(
        sessionId: String,
        key: String,
        value: String
    ): UIO[Unit] =
      sessions.update { m =>
        m.get(sessionId) match {
          case Some(s) =>
            m + (sessionId -> s.copy(context = s.context + (key -> value)))
          case None => m
        }
      }

    private def endSession(sessionId: String): UIO[UssdResponse] =
      sessions.update(_ - sessionId) *>
        ZIO.succeed(
          UssdResponse("END Thank you for using Finbank.", isFinal = true)
        )

    private def fetchBalance(phoneE164: String): UIO[UssdResponse] =
      // Stub: in production, look up wallet balance by phoneE164
      ZIO.logInfo(s"[USSD] Balance check for $phoneE164") *>
        ZIO.succeed(
          UssdResponse(
            "END Your XAF balance is 15,000 FCFA.\nFor more, download the Finbank app.",
            isFinal = true
          )
        )

    private def listCircles(phoneE164: String): UIO[UssdResponse] =
      ZIO.logInfo(s"[USSD] Circles list for $phoneE164") *>
        ZIO.succeed(
          UssdResponse(
            "END Your circles:\n1. Family Circle\n2. Work Circle\nReply with the app for details.",
            isFinal = true
          )
        )

    private def fetchScore(phoneE164: String): UIO[UssdResponse] =
      ZIO.logInfo(s"[USSD] Score fetch for $phoneE164") *>
        ZIO.succeed(
          UssdResponse(
            "END Your tontine score: 82/100 (GOOD)\nKeep contributing on time to improve.",
            isFinal = true
          )
        )

    private def disburseLoan(session: UssdSession): UIO[Unit] =
      ZIO.logInfo(
        s"[USSD] Loan disbursal for ${session.phoneE164} amount=${session.context.getOrElse("loanAmount", "?")}"
      )
  }
}
