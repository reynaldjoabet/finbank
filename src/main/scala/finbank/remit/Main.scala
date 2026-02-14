package finbank.remit

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port

object Main extends IOApp.Simple {
  val run =
    for server <- EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString("0.0.0.0").get)
        .withPort(Port.fromInt(8080).get)
        .withHttpApp(HttpRoutes.routes.orNotFound)
        .build
        .useForever
    yield ExitCode.Success
}
