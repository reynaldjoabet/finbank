# Copilot Instructions — finbank

## Build & Tooling
- **Scala 3.3.x** with braces style (`-no-indent -rewrite`). Never use significant-indentation syntax.
- **SBT** multi-module project. Always use `sbt --client` for commands (e.g. `sbt --client compile`, `sbt --client "njangi/run"`).
- Subprojects: `njangi`, `coinstar`, `billing`, `migrantbank`, `unity-pay`. Shared dependencies live in `project/Dependencies.scala`.

## Effect System — ZIO

- Use **ZIO 2** as the primary effect system (`ZIO`, `IO`, `UIO`, `Task`, `URIO`).
- Prefer typed error channels (`IO[AppError, A]`) over `Task[A]` / `Throwable`.
- Use `ZLayer` for dependency injection. Compose layers with `ZLayer.make[...]` or `.provide(...)`.
- Use `ZIO.logInfo`, `ZIO.logError`, `ZIO.logWarning`, `ZIO.logDebug` — never `println`.
- Use `ZIO.logSpan("name") { ... }` for structured tracing of business operations.
- Entry points extend `ZIOAppDefault` and override `def run`.

## Service Pattern

- Define a **trait** for the service contract.
- Implement with either an anonymous `new Trait { ... }` inside `ZLayer.fromFunction` or a named `XxxLive` class.
- Put the `ZLayer` in the companion object as `val live` or `val layer`.
- For coinstar-style services, add static accessor methods on the companion:
  ```scala
  ZIO.serviceWithZIO[T](_.method(...))
  ```

## IDs

- **Database**: Use **UUIDv7** for primary keys generated in SQL (e.g. PostgreSQL `uuid_generate_v7()` or `gen_random_uuid()`). UUIDv7 is time-ordered and better for index performance.
- **Scala**: Use `Random.nextUUID` (UUIDv4) since Java/Scala does not yet have native UUIDv7 support.
- Model IDs as **Scala 3 opaque types** wrapping `UUID`:
  ```scala
  opaque type FooId = UUID
  object FooId {
    def make: UIO[FooId] = Random.nextUUID
    def fromUUID(uuid: UUID): FooId = uuid
    def fromString(s: String): Option[FooId] = Try(UUID.fromString(s)).toOption
    extension (id: FooId) def value: UUID = id
    given JsonEncoder[FooId] = JsonEncoder.uuid.contramap[FooId](_.value)
    given JsonDecoder[FooId] = JsonDecoder.uuid
  }
  ```

## Error Handling

- Define domain errors as a `sealed trait XxxError extends Throwable` or `enum XxxError(msg: String) extends Throwable(msg)`.
- Common cases: `NotFound`, `Validation`, `Conflict`, `Forbidden`, `Unauthorized`, `Internal`.
- Map errors in `.catchAll` or `.mapError` — never let raw exceptions leak into HTTP responses.

## Domain Modeling

- Use **case classes** for entities and DTOs.
- Use **enums** for statuses, kinds, and categorical types.
- Store monetary amounts as **`Long` minor units** (cents/centimes), not `BigDecimal`.
- Use `Instant` (from `java.time`) for timestamps — never `Date` or epoch millis.

## JSON — zio-json

- Use **zio-json** for all serialization in ZIO subprojects.
- Prefer `derives JsonEncoder, JsonDecoder` or `derives JsonCodec` on case classes/enums.
- For opaque types, define manual `given` instances using `.contramap` / `.map`.
- Use `DeriveJsonCodec.gen[T]` in companions when `derives` doesn't work.

## JSON — jsoniter-scala (high-performance path)

- Use **jsoniter-scala** when maximum serialization performance is needed (e.g. high-throughput HTTP endpoints).
- Define codecs with `implicit val codec: JsonValueCodec[T] = JsonCodecMaker.make`.
- Parse with `readFromArray[T](bytes)` or `readFromString[T](str)`. Serialize with `writeToString(value)` or `writeToArray(value)`.
- jsoniter-scala throws exceptions by default (unlike zio-json's `Either`). Always wrap in `ZIO.attempt`:
  ```scala
  ZIO.attempt(readFromArray[User](bodyBytes))
    .mapError(e => Response.badRequest(s"Invalid JSON: ${e.getMessage}"))
  ```
- In zio-http handlers, prefer `req.body.asArray` over `req.body.asString` for zero-copy performance:
  ```scala
  val app: Routes[Any, Response] = Routes(
    Method.POST / "user" -> handler { (req: Request) =>
      for {
        bodyBytes <- req.body.asArray
        user <- ZIO.attempt(readFromArray[User](bodyBytes))
          .mapError(e => Response.badRequest(s"Invalid JSON: ${e.getMessage}"))
      } yield Response.json(writeToString(user))
    }
  )
  ```

## HTTP — zio-http

- Define routes as `Routes[Env, Nothing]` using `Routes(...)` with `handler { ... }` blocks.
- Parse path params with `string("name")` or `uuid("name")`.
- Parse request bodies: use `req.body.asArray` + jsoniter for performance, or `req.body.asString.flatMap(s => ZIO.fromEither(s.fromJson[T]))` with zio-json.
- Return errors via `.catchAll` mapping domain errors to appropriate HTTP status codes.
- Use `X-Correlation-Id` header for request tracing (fallback to random UUID).
- Prefix versioned routes with `/v1/`.

## Database

- **coinstar**: Quill (`Quill.Postgres[SnakeCase]`) — `inline def`, `querySchema`, `run(...)`, `lift(...)`.
- **migrantbank**: Magnum — `@Table(PostgresDbType)`, `derives DbCodec`, `@Id`, `sql"..."` interpolation, `DbCon ?=>` context functions.
- **njangi**: In-memory `Ref[Map[...]]` repos (will migrate to Quill or Magnum later).
- Use **Flyway** for schema migrations (`classpath:db/migration`).
- Use **HikariCP** for connection pooling.
- Use `ZLayer.scoped` + `ZIO.acquireRelease` for DataSource lifecycle.

## Configuration

- Use **zio-config** + **zio-config-magnolia** + **zio-config-typesafe** with HOCON `application.conf`.
- Derive config with `deriveConfig[AppConfig].nested("app")`.
- Nest config sections: `AppConfig.Http`, `AppConfig.Db`, `AppConfig.Security`, etc.

## Testing

- Use **ZIO Test** (`zio-test`, `zio-test-sbt`) with `ZTestFramework`.
- Use **zio-json-golden** for golden testing of JSON codecs.

## Security

- Hash passwords with **Argon2** via `password4j`.
- Encrypt PII with **AES-256-GCM**.
- Hash refresh tokens and SMS codes with **SHA-256**.
- Use **JWT** for auth tokens (nimbus-jose-jwt, jwt-zio-json, or auth0 java-jwt depending on subproject).
- Implement bearer-token middleware via `HandlerAspect` or manual header extraction.

## Patterns to Follow

- **Idempotency keys**: Store `(userId, idempotencyKey) → response` to prevent duplicate mutations.
- **Optimistic concurrency**: Use a `version` column; check `WHERE version = ?` on updates.
- **Retry**: `Schedule.exponential(...) && Schedule.recurs(n)` with `.jittered`.
- **Audit logging**: Log all mutations via `AuditRepo.append(kind, userId, correlationId, details)`.
- **Correlation IDs**: Thread `X-Correlation-Id` through the request lifecycle.
- **Parallel independent effects**: Use `zipPar` or `collectAllPar` for independent side-effects.

## Naming Conventions

- Packages: lowercase dot-separated (`tontine.service`, `coinstar.wallet.http`).
- Services: `XxxService` trait, `XxxServiceLive` or anonymous impl.
- Repos: `XxxRepo` trait, `XxxRepoLive` class.
- DB row types: `XxxRow` case class.
- DTOs: Separate file or `dto/` subpackage.
- Layer values: `.live`, `.layer`, or `.inMemory`.

## Style

- Always use **braces** — no indentation-based syntax.
- Use `given` / `derives` — never `implicit`.
- Use `extension` methods — never implicit classes.
- Use `for { ... } yield` for ZIO pipelines with multiple steps.