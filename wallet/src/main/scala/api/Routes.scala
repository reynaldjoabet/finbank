package api

import domain.*
import service.*
import zio.*
import zio.http.*
import zio.json.*

final case class CreateInvoiceRequest(
    reference: String,
    customerMsisdn: String,
    total: Money
)
object CreateInvoiceRequest {
  given JsonCodec[CreateInvoiceRequest] = DeriveJsonCodec.gen
}

final case class CreateInvoiceResponse(invoice: Invoice)
object CreateInvoiceResponse {
  given JsonCodec[CreateInvoiceResponse] = DeriveJsonCodec.gen
}

final case class PayInvoiceRequest(
    provider: Provider,
    idempotencyKey: String,
    callbackUrl: String
)
object PayInvoiceRequest {
  given JsonCodec[PayInvoiceRequest] = DeriveJsonCodec.gen
}

final case class PayInvoiceResponse(intent: PaymentIntent)
object PayInvoiceResponse {
  given JsonCodec[PayInvoiceResponse] = DeriveJsonCodec.gen
}

final case class ErrorResponse(error: String)
object ErrorResponse { given JsonCodec[ErrorResponse] = DeriveJsonCodec.gen }

object HttpRoutes {

  private def jsonError(msg: String, status: Status): Response = {
    Response.json(ErrorResponse(msg).toJson).copy(status = status)
  }

  def app: ZIO[PaymentService, Nothing, Routes[Any, Response]] = {
    ZIO.serviceWith[PaymentService] { svc =>
      Routes(
        Method.POST / "invoices" -> handler { (req: Request) =>
          (for {
            body <- req.body.asString
            in <- ZIO
              .fromEither(body.fromJson[CreateInvoiceRequest])
              .mapError(AppError.Validation.apply)
            // In real life, merchantId comes from JWT / API key auth
            merchantId = MerchantId.random
            inv <- svc.createInvoice(
              merchantId,
              in.reference,
              in.customerMsisdn,
              in.total
            )
          } yield Response.json(CreateInvoiceResponse(inv).toJson))
            .catchAll {
              case e: AppError.Validation =>
                ZIO.succeed(jsonError(e.getMessage, Status.BadRequest))
              case e =>
                ZIO.succeed(jsonError(e.getMessage, Status.InternalServerError))
            }
        },

        Method.GET / "invoices" / string("id") -> handler {
          (idStr: String, _: Request) =>
            val io =
              for {
                id <- ZIO
                  .attempt(java.util.UUID.fromString(idStr))
                  .map(InvoiceId.fromUUID)
                  .mapError(_ => AppError.Validation("Invalid invoice id"))
                // Again, merchantId should come from auth context
                inv <- svc match {
                  case s: PaymentService =>
                    // no direct get in service; you'd add it or go via repo. Kept short here.
                    ZIO.fail(
                      AppError.Validation(
                        "Add a getInvoice endpoint in the service for production"
                      )
                    )
                }
              } yield inv

            io.foldZIO(
              e => ZIO.succeed(jsonError(e.getMessage, Status.BadRequest)),
              _ => ZIO.succeed(Response.text("not implemented"))
            )
        },

        Method.POST / "invoices" / string("id") / "pay" -> handler {
          (idStr: String, req: Request) =>
            (for {
              body <- req.body.asString
              in <- ZIO
                .fromEither(body.fromJson[PayInvoiceRequest])
                .mapError(AppError.Validation.apply)
              invoiceId <- ZIO
                .attempt(java.util.UUID.fromString(idStr))
                .map(InvoiceId.fromUUID)
                .mapError(_ => AppError.Validation("Invalid invoice id"))
              merchantId = MerchantId.random
              intent <- svc.requestPayment(
                merchantId,
                invoiceId,
                in.provider,
                in.idempotencyKey,
                in.callbackUrl
              )
            } yield Response.json(PayInvoiceResponse(intent).toJson))
              .catchAll {
                case e: AppError.Validation =>
                  ZIO.succeed(jsonError(e.getMessage, Status.BadRequest))
                case e: AppError.Conflict =>
                  ZIO.succeed(jsonError(e.getMessage, Status.Conflict))
                case e =>
                  ZIO.succeed(
                    jsonError(e.getMessage, Status.InternalServerError)
                  )
              }
        },

        Method.POST / "webhooks" / string("provider") -> handler {
          (providerStr: String, req: Request) =>
            val headers = req.headers.toList
              .map(h => h.headerName.toString -> h.renderedValue)
              .toMap
            (for {
              raw <- req.body.asString.orElseSucceed("")
              provider <- ZIO
                .fromEither(providerStr.fromJson[Provider])
                .orElseSucceed {
                  providerStr.toLowerCase match {
                    case "mtnmomo"     => Provider.MtnMomo
                    case "orangemoney" => Provider.OrangeMoney
                    case "mpesa"       => Provider.MPesa
                    case "airtelmoney" => Provider.AirtelMoney
                    case _             => Provider.Sandbox
                  }
                }
              _ <- svc.handleWebhook(provider, headers, raw)
            } yield Response.status(Status.Ok)).catchAll {
              case e: AppError.Validation =>
                ZIO.succeed(jsonError(e.getMessage, Status.BadRequest))
              case e =>
                ZIO.succeed(jsonError(e.getMessage, Status.InternalServerError))
            }
        }
      )
    }
  }
}
