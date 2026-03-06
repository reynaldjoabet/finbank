package revenue.api

import zio.http.*
import revenue.repo.*
import revenue.service.*

object AppRoutes {

  type Env =
    AuthService & TaxpayerService & ReturnService & AssessmentService &
      PaymentService & RefundService & ObjectionService & CaseService &
      RiskRuleService & DocumentService & IntegrationService & AuditRepo

  val routes: Routes[Env, Nothing] =
    HealthRoutes.routes ++
      AuthRoutes.routes ++
      TaxpayerRoutes.routes ++
      ReturnRoutes.routes ++
      AssessmentRoutes.routes ++
      PaymentRoutes.routes ++
      RefundRoutes.routes ++
      ObjectionRoutes.routes ++
      CaseRoutes.routes ++
      AdminRoutes.routes ++
      IntegrationRoutes.routes
}
