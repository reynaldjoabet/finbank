package tontine
import tontine.http.*
import zio.*
import zio.http.*
import tontine.service.*
object Main extends ZIOAppDefault {

  private val httpApp: Routes[
    CircleService & MemberRepo & (ContributionService & ScoreService),
    Nothing
  ] =
    Api.routes @@ Middleware.debug

  private val layers: ZLayer[
    Any,
    Nothing,
    CircleRepo & MemberRepo & (ContributionRepo & AuditRepo) &
      (MobileMoneyGateway & OpenBankingClient) &
      (CircleService & ContributionService & ScoreService)
  ] =
    (CircleRepo.layer ++ MemberRepo.layer ++ ContributionRepo.layer ++ AuditRepo.layer) >+>
      (MobileMoneyGateway.layer ++ OpenBankingClient.layer) >+>
      (CircleService.live ++ ContributionService.live ++ ScoreService.live)

  override def run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] =
    Server
      .serve(httpApp)
      .provide(
        Server.defaultWithPort(8080),
        layers
      )

}
