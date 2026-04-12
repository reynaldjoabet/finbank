package unitypay
import zio._
import sttp.client4._
//import sttp.client4.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client4.GenericBackend
object UnityPay {

  case class PaymentRequest(
      senderId: String,
      amountNgn: BigDecimal,
      recipientPhone: String
  )
  case class Quote(rate: BigDecimal, fee: BigDecimal, totalUsdc: BigDecimal)

  trait UnityPayService {
    def processCrossBorder(req: PaymentRequest): Task[String]
  }

  case class UnityPayLive(backend: StreamBackend[Task, Any])
      extends UnityPayService {

    override def processCrossBorder(req: PaymentRequest): Task[String] = {
      for {
        // 1. Trigger Open Banking Pull (e.g., Mono/Stitch)
        _ <- Console.printLine(
          s"Pulling ${req.amountNgn} NGN from ${req.senderId} via Open Banking..."
        )
        _ <- triggerDirectDebit(req.amountNgn, req.senderId)

        // 2. Execute First Conversion (NGN -> USDC) via Liquidity Provider (e.g., Yellow Card)
        usdcAmount <- convertToStablecoin(req.amountNgn)
        _ <- Console.printLine(s"Converted to $usdcAmount USDC")

        // 3. Move USDC to Destination Wallet (The Blockchain Step)
        txHash <- transferOnChain(usdcAmount, "KENYA_VAULT_ADDRESS")

        // 4. Final Conversion & Disburse (USDC -> KES) to Mobile Money (e.g., M-Pesa)
        _ <- disburseToMobileMoney(usdcAmount, req.recipientPhone)
        _ <- Console.printLine(s"Payment successful for ${req.recipientPhone}!")
      } yield txHash
    }

    // Mock API Call: Open Banking Pull
    private def triggerDirectDebit(
        amount: BigDecimal,
        userId: String
    ): Task[Unit] = {
      // sttp call to Mono/Stitch API goes here
      ZIO.unit
    }

    // Mock API Call: Crypto Liquidity Provider
    private def convertToStablecoin(amountNgn: BigDecimal): Task[BigDecimal] = {
      // In a real app, this calls a provider like Yellow Card's API
      ZIO.succeed(amountNgn / 1500) // Mock exchange rate
    }

    private def transferOnChain(
        amount: BigDecimal,
        target: String
    ): Task[String] = {
      ZIO.succeed("0x742d35Cc6634C05321...") // Mock transaction hash
    }

    private def disburseToMobileMoney(
        amountUsdc: BigDecimal,
        phone: String
    ): Task[Unit] = {
      // sttp call to M-Pesa B2C API or a provider like Flutterwave
      ZIO.unit
    }
  }

  def secureTransfer(payload: String) = {
    val secret = "unity-internal-secret"
    val request = RequestSigner.authenticatedRequest(payload, secret)

    // Use a ZIO-compatible backend
    ZIO.serviceWithZIO[StreamBackend[Task, Any]] { backend =>
      request.send(backend).map(_.body.getOrElse("Error"))
    }
  }
}

import UnityPay._
object UnityPayService {

  val live: ZLayer[StreamBackend[Task, Any], Nothing, UnityPayService] =
    ZLayer {
      for {
        backend <- ZIO.service[StreamBackend[Task, Any]]
      } yield UnityPayLive(backend)
    }

  val live2 = ZLayer(ZIO.service[StreamBackend[Task, Any]].map(UnityPayLive(_)))
}
