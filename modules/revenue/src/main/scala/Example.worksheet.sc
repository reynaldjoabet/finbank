import zio._
// This is stack-safe in ZIO — 10 million flatMaps, no overflow:
def loop(n: Int): ZIO[Any, Nothing, Unit] =
  if (n <= 0) ZIO.unit
  else ZIO.succeed(n).flatMap(i => loop(i - 1))

// Because flatMap doesn't CALL loop — it returns FlatMap(Succeed(n), i => loop(i-1))
// The runtime trampoline executes it one step at a time.
