package revenue.api

import zio.http.*

object HealthRoutes {
  val routes: Routes[Any, Nothing] =
    Routes(
      Method.GET / "health" -> Handler.text("ok")
    )
}
