import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / dependencyOverrides ++= Seq(
  zioJson
)

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val commonScalacOptions = Seq(
  "-no-indent",
  "-deprecation", // Warns about deprecated APIs
  "-feature", // Warns about advanced language features
  "-unchecked",
  // "-Wunused:imports",
  //   "-Wunused:privates",
  //   "-Wunused:locals",
  //   "-Wunused:explicits",
  //   "-Wunused:implicits",
  //   "-Wunused:params",
  //   "-Wvalue-discard",
  "-language:strictEquality",
  "-Xmax-inlines:100",
  "-release:17"
)

lazy val commonSettings = Seq(
  scalacOptions := commonScalacOptions
)

val commonDependencies = Seq(
  sttpCore,
  sttpJsoniter,
  slf4j,
  logback,
  scribe,
  scribeSlf4j,
  scribeCats,
  jsoniter,
  jsoniterMacros,
  jsoniterCirce,
  zio,
  zioJson,
  zioTest,
  zioTestSbt,
  zioConfig,
  zioConfigMagnolia,
  caffeine,
  zioLogging,
  zioLoggingSlf4j,
  zioHttp,
  zioJsonGolden,
  zioSttp,
  zioKafka,
  magnum,
  password4j,
  postgres,
  zioConfigTypesafe,
  nimbusJoseJwt,
  nimbusOauth2Oidc,
  chimney,
  iron,
  ironChimney,
  ironDoobie,
  ironJsoniter,
  ironPureconfig,
  ironSkunk,
  ironZioJson,
  pureconfig
)

val dbDependencies = Seq(
  quill,
  hikaricp,
  flyway,
  jwtZioJson
)

// Shared by both OpenAPI client modules. The only differences between them are
// the project name and base directory, so the wiring lives in one place.
lazy val codegenSettings = Seq(
  openApiModelNamePrefix := "",
  openApiModelNameSuffix := "",
  openApiSkipOverwrite := Some(false),
  openApiRemoveOperationIdPrefix := Some(true),
  openApiGenerateMetadata := SettingDisabled,
  // Use the same JSON so CLI and SBT stay in sync
  openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,

  openApiIgnoreFileOverride := ((ThisBuild / baseDirectory).value / "modules" / ".openapi-generator-ignore").getPath,
  // Put generated sources where SBT expects managed sources
  openApiOutputDir := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
  openApiGenerateModelTests := SettingDisabled,
  openApiGenerateApiTests := SettingDisabled,
  // Fail fast on bad specs
  openApiValidateSpec := Some(true),

  // (Re)generate the client. Must stay uncached: sbt 2 caches `:=` task results
  // by default, but the cache key is built from the task's `.value` inputs, and
  // nothing here hashes the OpenAPI spec's *contents* -- sbt's own file-input
  // keys (allInputFiles / changedInputFiles) are @transient, i.e. deliberately
  // excluded from cache input. A Def.cachedTask would therefore keep serving a
  // stale client whenever the spec changed, so we always regenerate.
  //
  // openApiGenerate returns the exact Seq[File] it just wrote, so `generate`
  // (typed Seq[File], see Dependencies.scala) forwards that straight through --
  // no re-globbing of the output directory, which would also pick up stale
  // files left by a previous run that the current spec no longer produces. No
  // .scala filter is needed here: the .openapi-generator-ignore (see
  // openApiIgnoreFileOverride above) already keeps everything but Scala sources
  // out of the output dir, so every file returned is a compilation unit.
  generate := Def.uncached {
    openApiGenerate.value
  },
  // Wired in as a sourceGenerator, NOT as `compile.dependsOn(generate)`.
  // sbt collects `sources` by globbing src/main/scala in a task separate from
  // `compile`, and dependsOn only sequences generate ahead of `compile` -- not
  // ahead of that glob. So on a clean checkout the glob ran first, found
  // nothing, and codegen compiled 0 sources. A sourceGenerator feeds `sources`
  // directly, so sbt has to run it before compiling. Because `generate` is
  // already typed Seq[File], its own return value is what sourceGenerators
  // needs -- no wrapping Def.task/glob required.
  Compile / sourceGenerators += generate.taskValue,
  // Generated output lands directly in src/main/scala (see openApiOutputDir
  // above), so drop the unmanaged source dir -- otherwise the same files get
  // compiled twice, once via the sourceGenerator and once via the default glob.
  Compile / unmanagedSourceDirectories := Seq.empty,
  libraryDependencies ++= Seq(
    sttpJsoniter,
    jsoniter,
    jsoniterMacros,
    jsoniterCirce
  ),
  // Generated sources don't carry CanEqual givens for their enums, so
  // -language:strictEquality would fail here. Keep -release so the emitted
  // bytecode still matches the rest of the build.
  scalacOptions := Seq("-release:17")
)

/** Defines an OpenAPI client module named `id`, rooted at `modules/<id>`. Everything specific to the module -- input
  * spec, api/model packages, output dir -- lives in that directory's config.json (see codegenSettings), so the project
  * id is the only thing that varies between modules.
  */
def codegenModule(id: String): Project =
  Project(id, file(s"modules/$id"))
    .enablePlugins(OpenApiGeneratorPlugin)
    .settings(codegenSettings)
    .settings(name := id)

lazy val paymentInitiationCodegen = codegenModule("payment-initiation-codegen")
lazy val accountInformationCodegen = codegenModule("account-information-codegen")

lazy val codegenModules: Seq[Project] =
  Seq(paymentInitiationCodegen, accountInformationCodegen)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "finbank",
    libraryDependencies ++= commonDependencies ++ Seq(
      circeCore,
      circeGeneric,
      circeParser,
      http4sDsl,
      emberServer,
      emberClient,
      http4sCirce,
      catsEffect,
      fs2,
      http4sBackend,
      pureconfigGeneric,
      munit
    )
  )
  .aggregate(
    Seq[ProjectReference](
      unityPay,
      njangi,
      billing,
      coinstar,
      migrantbank,
      wallet,
      revenue
    ) ++ codegenModules.map(m => LocalProject(m.id)) *
  )

lazy val unityPay = (project in file("modules/unity-pay"))
  .settings(commonSettings)
  .settings(
    name := "unity-pay",
    libraryDependencies ++= commonDependencies
  )

lazy val njangi = (project in file("modules/njangi"))
  .settings(commonSettings)
  .settings(
    name := "njangi",
    libraryDependencies ++= commonDependencies
  )

lazy val billing = (project in file("modules/billing"))
  .settings(commonSettings)
  .settings(
    name := "billing",
    libraryDependencies ++= commonDependencies
  )

lazy val coinstar = (project in file("modules/coinstar"))
  .settings(commonSettings)
  .settings(
    name := "coinstar",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )

lazy val migrantbank = (project in file("modules/migrantbank"))
  .settings(commonSettings)
  .settings(
    name := "migrantbank",
    libraryDependencies ++= commonDependencies ++ dbDependencies ++ Seq(
      auth0
    )
  )

lazy val wallet = (project in file("modules/wallet"))
  .settings(commonSettings)
  .settings(
    name := "wallet",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )

lazy val revenue = (project in file("modules/revenue"))
  .settings(commonSettings)
  .settings(
    name := "revenue",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )
