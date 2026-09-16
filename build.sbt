import Dependencies.*

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / dependencyOverrides ++= Seq(
  zioJson
)

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-source:3.3",
  "-java-output-version:17",
  "-Werror",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wshadow:all",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val generatedScalacOptions = Seq(
  "-encoding",
  "UTF-8",
  "-java-output-version:17",
  "-Xmax-inlines:64"
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
  openApiModelNamePrefix         := "",
  openApiModelNameSuffix         := "",
  openApiSkipOverwrite           := Some(false),
  openApiRemoveOperationIdPrefix := Some(true),
  openApiGenerateMetadata        := SettingDisabled,
  // Use the same JSON so CLI and SBT stay in sync
  openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,
  // Shared ignore file lives one level up, in modules/ -- i.e. each module's
  // parent dir. getParentFile keeps the path normalized (no literal /../).
  openApiIgnoreFileOverride := (baseDirectory.value.getParentFile / ".openapi-generator-ignore").getPath,
  // Put generated sources where SBT expects managed sources
  openApiOutputDir          := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
  openApiGenerateModelTests := SettingDisabled,
  openApiGenerateApiTests   := SettingDisabled,
  // Fail fast on bad specs
  openApiValidateSpec := Some(true),

  generate := Def.uncached {
    openApiGenerate.value
  },

  Compile / sourceGenerators += generate.taskValue,

  Compile / unmanagedSourceDirectories := Seq.empty,
  libraryDependencies                 ++= Seq(
    sttpJsoniter,
    jsoniter,
    jsoniterMacros,
    jsoniterCirce
  )
)

def codegenModule(id: String): Project =
  Project(id, file(s"modules/$id"))
    .enablePlugins(OpenApiGeneratorPlugin)
    .settings(codegenSettings)
    .settings(name := id)
    .settings(scalacOptions := generatedScalacOptions)

lazy val paymentInitiationCodegen  = codegenModule("payment-initiation-codegen")
lazy val accountInformationCodegen = codegenModule("account-information-codegen")

lazy val codegenModules: Seq[Project] =
  Seq(paymentInitiationCodegen, accountInformationCodegen)

lazy val root = (project in file("."))
  .settings(
    name                 := "finbank",
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
  .settings(
    name                 := "unity-pay",
    libraryDependencies ++= commonDependencies
  )

lazy val njangi = (project in file("modules/njangi"))
  .settings(
    name                 := "njangi",
    libraryDependencies ++= commonDependencies
  )

lazy val billing = (project in file("modules/billing"))
  .settings(
    name                 := "billing",
    libraryDependencies ++= commonDependencies
  )

lazy val coinstar = (project in file("modules/coinstar"))
  .settings(
    name                 := "coinstar",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )

lazy val migrantbank = (project in file("modules/migrantbank"))
  .settings(
    name                 := "migrantbank",
    libraryDependencies ++= commonDependencies ++ dbDependencies ++ Seq(
      auth0
    )
  )

lazy val wallet = (project in file("modules/wallet"))
  .settings(
    name                 := "wallet",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )

lazy val revenue = (project in file("modules/revenue"))
  .settings(
    name                 := "revenue",
    libraryDependencies ++= commonDependencies ++ dbDependencies
  )
