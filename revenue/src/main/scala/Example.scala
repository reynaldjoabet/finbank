package revenue
import zio._
import java.io.IOException
object Example extends ZIOAppDefault {
  // This is stack-safe in ZIO — 10 million flatMaps, no overflow:
  def loop(n: Int): ZIO[Any, IOException, Unit] =
    if (n <= 0) ZIO.unit
    else
      ZIO
        .succeed(n)
        .flatMap(n => Console.printLine(n).as(n))
        .flatMap(i => loop(i - 1))

  // Because flatMap doesn't CALL loop — it returns FlatMap(Succeed(n), i => loop(i-1))
  // The runtime trampoline executes it one step at a time.

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = loop(10000000)
}
