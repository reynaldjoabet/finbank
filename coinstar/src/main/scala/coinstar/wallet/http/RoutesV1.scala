package coinstar.wallet.http
import io.getquill.autoQuote
import coinstar.wallet.config.AppConfig
import coinstar.wallet.domain.*
import coinstar.wallet.http.dto.*
import coinstar.wallet.http.middleware.AuthAspect
import io.getquill.autoQuote
import coinstar.wallet.service.{AuthService, IdempotencyService, WalletService}
import coinstar.wallet.util.{CryptoHash, MoneyFormat}
import zio.*
import zio.http.*
import zio.http.codec.PathCodec.string
import zio.json.*

import java.util.UUID

final class RoutesV1(cfg: AppConfig) {

  import HttpSupport.*

  private def headerValue(req: Request, name: String): Option[String] =
    // Headers is Iterable[Header]; Custom headers are represented as Header.Custom(customName, value)
    req.headers.collectFirst {
      case Header.Custom(customName, value)
          if customName.toString().equalsIgnoreCase(name) =>
        value.toString()
    }

  private val publicRoutes: Routes[WalletService, Response] =
    Routes(
      Method.GET / "health" -> Handler.ok,
      Method.GET / "ready" -> handler {
        WalletService.readiness
          .as(Response.ok)
          .catchAll(e =>
            ZIO.succeed(
              errorResponse(
                Status.ServiceUnavailable,
                "not_ready",
                e.getMessage()
              )
            )
          )
      }
    )

  private val devRoutes: Routes[AuthService, Response] =
    Routes(
      Method.POST / "v1" / "auth" / "dev-token" ->
        handler { (req: Request) =>
          if cfg.env != "dev" then
            ZIO.succeed(
              errorResponse(Status.NotFound, "not_found", "Not found")
            )
          else
            (for {
              dto <- decodeJson[DevTokenRequest](req)
              uid <- ZIO
                .fromEither(UserId.fromString(dto.user_id))
                .mapError(msg =>
                  errorResponse(Status.BadRequest, "bad_request", msg)
                )
              tok <- AuthService.issueDevToken(uid)
            } yield jsonResponse(DevTokenResponse(tok), Status.Ok)
          )
        }
    )

  private val protectedRoutes
      : Routes[WalletService & IdempotencyService & AuthService, Response] =
    Routes(
      Method.GET / "v1" / "wallets" ->
        handler { (_: Request) =>
          for {
            principal <- ZIO.service[Principal]
            wallets <- WalletService
              .listWallets(principal.userId)
              .mapError(e =>
                errorResponse(
                  Status.InternalServerError,
                  "internal",
                  e.getMessage()
                )
              )
          } yield {
            val dtos = wallets.map { w =>
              WalletDto(
                id = w.id.value.toString,
                asset = w.asset.code,
                balance_minor = w.balanceMinor,
                decimals = w.asset.decimals,
                balance = MoneyFormat.toDisplay(w.balanceMinor, w.asset)
              )
            }
            jsonResponse(dtos, Status.Ok)
          }
        } @@ AuthAspect.bearer,

      Method.POST / "v1" / "wallets" ->
        handler { (req: Request) =>
          for {
            principal <- ZIO.service[Principal]
            idemKey <- ZIO
              .fromOption(headerValue(req, "Idempotency-Key"))
              .orElseFail(
                errorResponse(
                  Status.BadRequest,
                  "bad_request",
                  "Missing Idempotency-Key header"
                )
              )

            body <- req.body.asString.mapError(_ =>
              errorResponse(Status.BadRequest, "bad_request", "Invalid body")
            )
            dto <- ZIO.fromEither(
              body
                .fromJson[CreateWalletRequest]
                .left
                .map(err =>
                  errorResponse(Status.BadRequest, "bad_request", err)
                )
            )
            asset <- ZIO
              .fromEither(Asset.fromCode(dto.asset))
              .mapError(msg =>
                errorResponse(Status.BadRequest, "bad_request", msg)
              )
            reqHash = CryptoHash.sha256Hex(body)
            wallet <- IdempotencyService
              .run(principal.userId, idemKey, reqHash) {
                WalletService.createWallet(principal.userId, asset)
              }
              .mapError(e =>
                errorResponse(Status.Conflict, "conflict", e.getMessage())
              )
          } yield {
            val out = WalletDto(
              id = wallet.id.value.toString,
              asset = wallet.asset.code,
              balance_minor = wallet.balanceMinor,
              decimals = wallet.asset.decimals,
              balance = MoneyFormat.toDisplay(wallet.balanceMinor, wallet.asset)
            )
            jsonResponse(out, Status.Created)
          }
        } @@ AuthAspect.bearer,

      Method.GET / "v1" / "wallets" / string("id") / "balance" ->
        handler { (id: String, _: Request) =>
          for {
            principal <- ZIO.service[Principal]
            wid <- ZIO
              .attempt(WalletId(UUID.fromString(id)))
              .mapError(_ =>
                errorResponse(
                  Status.BadRequest,
                  "bad_request",
                  "Invalid wallet id"
                )
              )
            wallet <- WalletService
              .getWallet(principal.userId, wid)
              .mapError {
                case DomainError.NotFound(m) =>
                  errorResponse(Status.NotFound, "not_found", m)
                case DomainError.Forbidden(m) =>
                  errorResponse(Status.Forbidden, "forbidden", m)
                case DomainError.Validation(m) =>
                  errorResponse(Status.BadRequest, "bad_request", m)
                case DomainError.Conflict(m) =>
                  errorResponse(Status.Conflict, "conflict", m)
                case other =>
                  errorResponse(
                    Status.InternalServerError,
                    "internal",
                    other.getMessage()
                  )
              }
          } yield {
            val out = WalletDto(
              id = wallet.id.value.toString,
              asset = wallet.asset.code,
              balance_minor = wallet.balanceMinor,
              decimals = wallet.asset.decimals,
              balance = MoneyFormat.toDisplay(wallet.balanceMinor, wallet.asset)
            )
            jsonResponse(out, Status.Ok)
          }
        } @@ AuthAspect.bearer,

      Method.POST / "v1" / "kiosk" / "vouchers" / "redeem" ->
        handler { (req: Request) =>
          for {
            principal <- ZIO.service[Principal]
            idemKey <- ZIO
              .fromOption(headerValue(req, "Idempotency-Key"))
              .orElseFail(
                errorResponse(
                  Status.BadRequest,
                  "bad_request",
                  "Missing Idempotency-Key header"
                )
              )

            body <- req.body.asString.mapError(_ =>
              errorResponse(Status.BadRequest, "bad_request", "Invalid body")
            )
            dto <- ZIO.fromEither(
              body
                .fromJson[RedeemVoucherRequest]
                .left
                .map(err =>
                  errorResponse(Status.BadRequest, "bad_request", err)
                )
            )
            wid <- ZIO
              .attempt(UUID.fromString(dto.wallet_id))
              .mapError(_ =>
                errorResponse(
                  Status.BadRequest,
                  "bad_request",
                  "Invalid wallet_id"
                )
              )
            reqHash = CryptoHash.sha256Hex(body)
            txId <- IdempotencyService
              .run(principal.userId, idemKey, reqHash) {
                WalletService.redeemVoucher(
                  principal.userId,
                  WalletId(wid),
                  dto.voucher_code
                )
              }
              .mapError {
                case DomainError.NotFound(m) =>
                  errorResponse(Status.NotFound, "not_found", m)
                case DomainError.Validation(m) =>
                  errorResponse(Status.BadRequest, "bad_request", m)
                case DomainError.Conflict(m) =>
                  errorResponse(Status.Conflict, "conflict", m)
                case other =>
                  errorResponse(
                    Status.InternalServerError,
                    "internal",
                    other.getMessage()
                  )
              }
          } yield jsonResponse(
            RedeemVoucherResponse(txId.value.toString),
            Status.Ok
          )
        } @@ AuthAspect.bearer
    )

  val routes
      : Routes[WalletService & IdempotencyService & AuthService, Response] =
    publicRoutes ++ devRoutes ++ protectedRoutes
}

object RoutesV1 {
  val layer: ZLayer[AppConfig, Nothing, RoutesV1] =
    ZLayer.fromFunction(new RoutesV1(_))
}
