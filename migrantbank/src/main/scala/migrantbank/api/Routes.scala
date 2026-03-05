package com.migrantbank.api

import com.migrantbank.config.AppConfig
import com.migrantbank.domain.*
import com.migrantbank.metrics.Metrics
import com.migrantbank.ratelimit.RateLimiter
import com.migrantbank.security.JwtService
import com.migrantbank.service.*
import zio.*
import zio.http.*
import zio.http.codec.PathCodec.uuid
import zio.json.*

import HttpUtils.*
import AuthHelpers.*

object AppRoutes {

  type Env =
    AppConfig & JwtService & Metrics & RateLimiter & RegistrationService &
      AuthService & AccountService & FundingService & TransferService &
      FamilyService & PaycheckService & LoanService & SupportService &
      AdminService & CardService

  private def withMetrics(
      name: String,
      effect: ZIO[Env, Response, Response]
  ): ZIO[Env, Nothing, Response] =
    effect
      .tap(_ => ZIO.serviceWithZIO[Metrics](_.inc(s"http_${name}_ok")))
      .catchAll { resp =>
        ZIO.serviceWithZIO[Metrics](_.inc(s"http_${name}_err")).as(resp)
      }

  val routes: Routes[Env, Nothing] =
    Routes(
      // ---- Health ----
      Method.GET / "health" / "live" -> handler(Response.text("ok")),
      Method.GET / "health" / "ready" -> handler(Response.text("ready")),

      // ---- Metrics ----
      Method.GET / "metrics" -> handler {
        ZIO.serviceWithZIO[Metrics](_.snapshot).map { snap =>
          val body = snap.toList
            .sortBy(_._1)
            .map { case (k, v) => s"$k $v" }
            .mkString("\n") + "\n"
          Response.text(body)
        }
      },

      // ---- Registration ----
      Method.POST / "v1" / "registration" / "start" -> handler {
        (req: Request) =>
          withMetrics(
            "registration_start",
            (for {
              cid <- ZIO.succeed(correlationId(req))
              dto <- parseJson[StartRegistrationRequest](req)
              res <- ZIO
                .serviceWithZIO[RegistrationService](
                  _.start(dto.toProfile, cid)
                )
                .mapError(mapError)
            } yield jsonResponse(
              StartRegistrationResponse(res._1, res._2),
              Status.Created
            )).mapError(identity)
          )
      },

      Method.POST / "v1" / "registration" / "confirm" -> handler {
        (req: Request) =>
          withMetrics(
            "registration_confirm",
            (for {
              cid <- ZIO.succeed(correlationId(req))
              dto <- parseJson[ConfirmRegistrationRequest](req)
              res <- ZIO
                .serviceWithZIO[RegistrationService](
                  _.confirm(dto.userId, dto.smsCode, dto.password, cid)
                )
                .mapError(mapError)
            } yield jsonResponse(res, Status.Ok)).mapError(identity)
          )
      },

      // ---- Auth ----
      Method.POST / "v1" / "auth" / "login" -> handler { (req: Request) =>
        withMetrics(
          "auth_login",
          (for {
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[LoginRequest](req)
            tokens <- ZIO
              .serviceWithZIO[AuthService](
                _.login(dto.phone, dto.password, cid)
              )
              .mapError(mapError)
          } yield jsonResponse(tokens)).mapError(identity)
        )
      },

      Method.POST / "v1" / "auth" / "refresh" -> handler { (req: Request) =>
        withMetrics(
          "auth_refresh",
          (for {
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[RefreshRequest](req)
            tokens <- ZIO
              .serviceWithZIO[AuthService](
                _.refresh(dto.userId, dto.refreshToken, cid)
              )
              .mapError(mapError)
          } yield jsonResponse(tokens)).mapError(identity)
        )
      },

      Method.POST / "v1" / "auth" / "logout" -> handler { (req: Request) =>
        withMetrics(
          "auth_logout",
          (for {
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[LogoutRequest](req)
            _ <- ZIO
              .serviceWithZIO[AuthService](
                _.logout(dto.userId, dto.refreshToken, cid)
              )
              .mapError(mapError)
          } yield Response.text("ok")).mapError(identity)
        )
      },

      // ---- Accounts ----
      Method.GET / "v1" / "accounts" / "me" -> handler { (req: Request) =>
        withMetrics(
          "accounts_me",
          (for {
            ctx <- requireAuth(req)
            cfg <- ZIO.service[AppConfig]
            _ <- ZIO
              .serviceWithZIO[RateLimiter](
                _.check(
                  s"${ctx.userId}:accounts_me",
                  cfg.rateLimit.requestsPerMinute
                )
              )
              .mapError(mapError)
            acc <- ZIO
              .serviceWithZIO[AccountService](_.me(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(acc)).mapError(identity)
        )
      },

      // ---- Cards + Wallet ----
      Method.GET / "v1" / "cards" / "me" -> handler { (req: Request) =>
        withMetrics(
          "cards_list",
          (for {
            ctx <- requireAuth(req)
            cs <- ZIO
              .serviceWithZIO[CardService](_.list(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(cs)).mapError(identity)
        )
      },

      Method.GET / "v1" / "wallet" / "provisioning" / uuid(
        "cardId"
      ) -> handler { (cardId: java.util.UUID, req: Request) =>
        withMetrics(
          "wallet_provisioning",
          (for {
            ctx <- requireAuth(req)
            res <- ZIO
              .serviceWithZIO[CardService](
                _.walletProvisioning(ctx.userId, cardId)
              )
              .mapError(mapError)
          } yield jsonResponse(res)).mapError(identity)
        )
      },

      // ---- Funding ----
      Method.POST / "v1" / "funding" / "topup" -> handler { (req: Request) =>
        withMetrics(
          "funding_topup",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            cfg <- ZIO.service[AppConfig]
            _ <- ZIO
              .serviceWithZIO[RateLimiter](
                _.check(
                  s"${ctx.userId}:funding_topup",
                  cfg.rateLimit.requestsPerMinute
                )
              )
              .mapError(mapError)
            dto <- parseJson[TopUpRequest](req)
            acc <- ZIO
              .serviceWithZIO[FundingService](
                _.topUp(
                  ctx.userId,
                  Money(dto.amountMinor, dto.currency),
                  dto.source,
                  cid
                )
              )
              .mapError(mapError)
          } yield jsonResponse(acc, Status.Ok)).mapError(identity)
        )
      },

      Method.POST / "v1" / "funding" / "cash-deposit" -> handler {
        (req: Request) =>
          withMetrics(
            "funding_cash_deposit",
            (for {
              ctx <- requireAuth(req)
              cid <- ZIO.succeed(correlationId(req))
              cfg <- ZIO.service[AppConfig]
              _ <- ZIO
                .serviceWithZIO[RateLimiter](
                  _.check(
                    s"${ctx.userId}:funding_cash",
                    cfg.rateLimit.requestsPerMinute
                  )
                )
                .mapError(mapError)
              dto <- parseJson[CashDepositRequest](req)
              acc <- ZIO
                .serviceWithZIO[FundingService](
                  _.cashDeposit(
                    ctx.userId,
                    Money(dto.amountMinor, dto.currency),
                    dto.branchRef,
                    cid
                  )
                )
                .mapError(mapError)
            } yield jsonResponse(acc, Status.Ok)).mapError(identity)
          )
      },

      // ---- Transfers ----
      Method.POST / "v1" / "transfers" / "p2p" -> handler { (req: Request) =>
        withMetrics(
          "transfer_p2p",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            cfg <- ZIO.service[AppConfig]
            _ <- ZIO
              .serviceWithZIO[RateLimiter](
                _.check(
                  s"${ctx.userId}:transfer_p2p",
                  cfg.rateLimit.requestsPerMinute
                )
              )
              .mapError(mapError)
            dto <- parseJson[P2PTransferRequest](req)
            idem = idempotencyKey(req)
            t <- ZIO
              .serviceWithZIO[TransferService](
                _.p2p(
                  ctx.userId,
                  dto.toUserId,
                  Money(dto.amountMinor, dto.currency),
                  dto.note,
                  idem,
                  cid
                )
              )
              .mapError(mapError)
          } yield jsonResponse(t, Status.Created)).mapError(identity)
        )
      },

      Method.POST / "v1" / "transfers" / "ach" -> handler { (req: Request) =>
        withMetrics(
          "transfer_ach",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            cfg <- ZIO.service[AppConfig]
            _ <- ZIO
              .serviceWithZIO[RateLimiter](
                _.check(
                  s"${ctx.userId}:transfer_ach",
                  cfg.rateLimit.requestsPerMinute
                )
              )
              .mapError(mapError)
            dto <- parseJson[AchTransferRequest](req)
            idem = idempotencyKey(req)
            t <- ZIO
              .serviceWithZIO[TransferService](
                _.ach(
                  ctx.userId,
                  dto.destination,
                  Money(dto.amountMinor, dto.currency),
                  dto.note,
                  idem,
                  cid
                )
              )
              .mapError(mapError)
          } yield jsonResponse(t, Status.Created)).mapError(identity)
        )
      },

      Method.GET / "v1" / "transfers" / "me" -> handler { (req: Request) =>
        withMetrics(
          "transfer_list",
          (for {
            ctx <- requireAuth(req)
            cfg <- ZIO.service[AppConfig]
            _ <- ZIO
              .serviceWithZIO[RateLimiter](
                _.check(
                  s"${ctx.userId}:transfer_list",
                  cfg.rateLimit.requestsPerMinute
                )
              )
              .mapError(mapError)
            ts <- ZIO
              .serviceWithZIO[TransferService](_.list(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(ts)).mapError(identity)
        )
      },

      // ---- Family Mode ----
      Method.POST / "v1" / "family" / "groups" -> handler { (req: Request) =>
        withMetrics(
          "family_create",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[CreateFamilyGroupRequest](req)
            g <- ZIO
              .serviceWithZIO[FamilyService](
                _.create(ctx.userId, dto.memberUserIds, cid)
              )
              .mapError(mapError)
          } yield jsonResponse(g, Status.Created)).mapError(identity)
        )
      },

      Method.GET / "v1" / "family" / "groups" -> handler { (req: Request) =>
        withMetrics(
          "family_list",
          (for {
            ctx <- requireAuth(req)
            gs <- ZIO
              .serviceWithZIO[FamilyService](_.list(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(gs)).mapError(identity)
        )
      },

      Method.POST / "v1" / "family" / "distribute" -> handler {
        (req: Request) =>
          withMetrics(
            "family_distribute",
            (for {
              ctx <- requireAuth(req)
              cid <- ZIO.succeed(correlationId(req))
              dto <- parseJson[FamilyDistributeRequest](req)
              ts <- ZIO
                .serviceWithZIO[FamilyService](
                  _.distribute(ctx.userId, dto.groupId, dto.payouts, cid)
                )
                .mapError(mapError)
            } yield jsonResponse(ts, Status.Created)).mapError(identity)
          )
      },

      // ---- Paycheck ----
      Method.POST / "v1" / "paycheck" / "enroll" -> handler { (req: Request) =>
        withMetrics(
          "paycheck_enroll",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[EnrollPaycheckRequest](req)
            e <- ZIO
              .serviceWithZIO[PaycheckService](
                _.enroll(ctx.userId, dto.employerName, cid)
              )
              .mapError(mapError)
          } yield jsonResponse(e, Status.Created)).mapError(identity)
        )
      },

      Method.GET / "v1" / "paycheck" / "me" -> handler { (req: Request) =>
        withMetrics(
          "paycheck_get",
          (for {
            ctx <- requireAuth(req)
            e <- ZIO
              .serviceWithZIO[PaycheckService](_.get(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(e)).mapError(identity)
        )
      },

      // ---- Loans ----
      Method.GET / "v1" / "loans" / "quote" -> handler { (req: Request) =>
        withMetrics(
          "loan_quote",
          (for {
            ctx <- requireAuth(req)
            q <- ZIO
              .serviceWithZIO[LoanService](_.quote(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(q)).mapError(identity)
        )
      },

      Method.POST / "v1" / "loans" / "request" -> handler { (req: Request) =>
        withMetrics(
          "loan_request",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[LoanRequest](req)
            l <- ZIO
              .serviceWithZIO[LoanService](
                _.request(ctx.userId, Money(dto.amountMinor, dto.currency), cid)
              )
              .mapError(mapError)
          } yield jsonResponse(l, Status.Created)).mapError(identity)
        )
      },

      Method.GET / "v1" / "loans" / "me" -> handler { (req: Request) =>
        withMetrics(
          "loan_list",
          (for {
            ctx <- requireAuth(req)
            ls <- ZIO
              .serviceWithZIO[LoanService](_.list(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(ls)).mapError(identity)
        )
      },

      // ---- Support ----
      Method.POST / "v1" / "support" / "tickets" -> handler { (req: Request) =>
        withMetrics(
          "ticket_create",
          (for {
            ctx <- requireAuth(req)
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[CreateTicketRequest](req)
            t <- ZIO
              .serviceWithZIO[SupportService](
                _.create(ctx.userId, dto.message, cid)
              )
              .mapError(mapError)
          } yield jsonResponse(t, Status.Created)).mapError(identity)
        )
      },

      Method.GET / "v1" / "support" / "tickets" -> handler { (req: Request) =>
        withMetrics(
          "ticket_list",
          (for {
            ctx <- requireAuth(req)
            ts <- ZIO
              .serviceWithZIO[SupportService](_.list(ctx.userId))
              .mapError(mapError)
          } yield jsonResponse(ts)).mapError(identity)
        )
      },

      // ---- Admin ----
      Method.GET / "admin" / "kyc" / "pending" -> handler { (req: Request) =>
        withMetrics(
          "admin_kyc_pending",
          (for {
            _ <- requireAdmin(req)
            u <- ZIO
              .serviceWithZIO[AdminService](_.pendingKyc())
              .mapError(mapError)
          } yield jsonResponse(u)).mapError(identity)
        )
      },

      Method.POST / "admin" / "kyc" / uuid("userId") / "status" -> handler {
        (userId: java.util.UUID, req: Request) =>
          withMetrics(
            "admin_kyc_set",
            (for {
              _ <- requireAdmin(req)
              cid <- ZIO.succeed(correlationId(req))
              dto <- parseJson[AdminSetKycRequest](req)
              _ <- ZIO
                .serviceWithZIO[AdminService](_.setKyc(userId, dto.status, cid))
                .mapError(mapError)
            } yield Response.text("ok")).mapError(identity)
          )
      },

      Method.POST / "admin" / "cards" / uuid("cardId") / "delivery" -> handler {
        (cardId: java.util.UUID, req: Request) =>
          withMetrics(
            "admin_card_delivery",
            (for {
              _ <- requireAdmin(req)
              cid <- ZIO.succeed(correlationId(req))
              dto <- parseJson[AdminUpdateDeliveryRequest](req)
              _ <- ZIO
                .serviceWithZIO[AdminService](
                  _.updateCardDelivery(cardId, dto.status, cid)
                )
                .mapError(mapError)
            } yield Response.text("ok")).mapError(identity)
          )
      },

      Method.POST / "admin" / "transfers" / uuid(
        "transferId"
      ) / "status" -> handler { (transferId: java.util.UUID, req: Request) =>
        withMetrics(
          "admin_transfer_status",
          (for {
            _ <- requireAdmin(req)
            cid <- ZIO.succeed(correlationId(req))
            dto <- parseJson[AdminTransferStatusRequest](req)
            _ <- ZIO
              .serviceWithZIO[TransferService](
                _.adminSetStatus(transferId, dto.status, cid)
              )
              .mapError(mapError)
          } yield Response.text("ok")).mapError(identity)
        )
      },

      Method.GET / "admin" / "analytics" -> handler { (req: Request) =>
        withMetrics(
          "admin_analytics",
          (for {
            _ <- requireAdmin(req)
            a <- ZIO
              .serviceWithZIO[AdminService](_.analytics())
              .mapError(mapError)
          } yield jsonResponse(a)).mapError(identity)
        )
      },

      Method.GET / "admin" / "audit" -> handler { (req: Request) =>
        withMetrics(
          "admin_audit",
          (for {
            _ <- requireAdmin(req)
            events <- ZIO
              .serviceWithZIO[AdminService](_.audit(200))
              .mapError(mapError)
          } yield jsonResponse(events)).mapError(identity)
        )
      },

      Method.GET / "admin" / "fraud" / "flagged" -> handler { (req: Request) =>
        withMetrics(
          "admin_fraud_flagged",
          (for {
            _ <- requireAdmin(req)
            ts <- ZIO
              .serviceWithZIO[AdminService](_.flaggedTransfers(200))
              .mapError(mapError)
          } yield jsonResponse(ts)).mapError(identity)
        )
      }
    )
}
