package finbank.remit

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.generic.auto._
import org.http4s.implicits._

object HttpRoutes {
  given EntityDecoder[IO, QuoteService.QuoteRequest] = jsonOf
  given EntityDecoder[IO, QuoteService.SendRequest] = jsonOf
  given EntityEncoder[IO, QuoteService.RouteOption] = jsonEncoderOf
  given EntityEncoder[IO, List[QuoteService.RouteOption]] = jsonEncoderOf
  given EntityEncoder[IO, QuoteService.SendResult] = jsonEncoderOf

  val routes: org.http4s.HttpRoutes[IO] = org.http4s.HttpRoutes.of[IO] {
    case GET -> Root => Ok("CorridorOne - MUR -> Cameroon Remit API (http4s)")

    case req @ POST -> Root / "api" / "quote" =>
      for
        body <- req.as[QuoteService.QuoteRequest]
        quotes = QuoteService.bestQuotes(body)
        resp <- Ok(quotes)
      yield resp

    case req @ POST -> Root / "api" / "send" =>
      for
        body <- req.as[QuoteService.SendRequest]
        res = QuoteService.executeSend(body)
        resp <- Ok(res)
      yield resp

  }

}
