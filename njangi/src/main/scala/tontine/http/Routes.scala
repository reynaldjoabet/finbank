package tontine.http

import zio.*
import zio.http.*
import zio.json.*
import tontine.*
// 1. The Data Model for the incoming Webhook Payload
case class MoMoCallbackResult(
    transactionId: String,
    externalReference: String,
    status: String,
    amount: BigDecimal,
    currency: String
)

object MoMoCallbackResult {
  // Automatically generates the JSON decoder for this case class
  implicit val decoder: JsonDecoder[MoMoCallbackResult] =
    DeriveJsonDecoder.gen[MoMoCallbackResult]
}

// 2. The HTTP Routes Definition
object MoMoRoutes {

  // We define routes that require our business logic services
  val routes: Routes[Any, Response] = Routes(
    // POST /momo/callback
    Method.POST / "momo" / "callback" -> handler { (req: Request) =>
      for {
        // Safely extract the body as a string
        bodyStr <- req.body.asString.orElseSucceed("")
        _ <- ZIO.logInfo(s"Received MoMo webhook payload: $bodyStr")

        // Attempt to parse the JSON into our case class
        response <- bodyStr.fromJson[MoMoCallbackResult] match {
          case Left(error) =>
            ZIO.logError(s"Failed to parse MoMo webhook: $error") *>
              ZIO.succeed(Response.badRequest)

          case Right(callback) =>
            // Handle the business logic based on the status
            if (callback.status == "SUCCESSFUL") {
              ZIO.logInfo(
                s"Contribution collected! TxID: ${callback.transactionId}, Amount: ${callback.amount}"
              ) *>
                // TODO: Update the Contribution record in PostgreSQL to "Success"
                ZIO.succeed(Response.ok)
            } else {
              ZIO.logWarning(
                s"Contribution failed. TxID: ${callback.transactionId}, Reason: ${callback.status}"
              ) *>
                // TODO: Mark Contribution as "Failed" and trigger retry logic
                ZIO.succeed(Response.ok)
            }
        }
      } yield response
    }
  )
}
