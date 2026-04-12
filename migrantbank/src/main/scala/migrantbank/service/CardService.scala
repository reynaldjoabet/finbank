package migrantbank.service

import migrantbank.db.Db
import migrantbank.domain.{*, given}
import migrantbank.repo.CardRepo
import zio.*
import java.util.UUID

trait CardService {
  def list(userId: UUID): IO[AppError, List[Card]]
  def walletProvisioning(
      userId: UUID,
      cardId: UUID
  ): IO[AppError, CardService.WalletProvisioningResponse]
}

object CardService {

  final case class WalletProvisioningResponse(
      cardId: UUID,
      provider: String,
      tokenizationData: Map[String, String]
  ) derives zio.json.JsonEncoder,
        zio.json.JsonDecoder

  val live: ZLayer[Db, Nothing, CardService] =
    ZLayer.fromFunction { (db: Db) =>
      new CardService {

        override def list(userId: UUID): IO[AppError, List[Card]] =
          db.query {
            CardRepo.listByUser(userId)
          }

        override def walletProvisioning(
            userId: UUID,
            cardId: UUID
        ): IO[AppError, WalletProvisioningResponse] =
          for {
            cards <- db.query { CardRepo.listByUser(userId) }
            card <- ZIO
              .fromOption(cards.find(_.id == cardId))
              .orElseFail(
                AppError.NotFound(s"Card $cardId not found for user $userId")
              )
            // Using ZIO Random instead of java.util.UUID.randomUUID()
            nonce <- Random.nextUUID
          } yield WalletProvisioningResponse(
            cardId = card.id,
            provider = "DUMMY_TOKENIZATION",
            tokenizationData = Map(
              "nonce" -> nonce.toString,
              "publicKey" -> "REPLACE_WITH_PROVIDER_KEY",
              "signature" -> "REPLACE_WITH_PROVIDER_SIGNATURE"
            )
          )
      }
    }
}
