import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalacOptions ++= Seq(
  "-no-indent",
  "-rewrite",
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
  "-Xmax-inlines:100"
)

lazy val root = (project in file("."))
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
      chimney,
      http4sBackend,
      pureconfig,
      pureconfigGeneric,
      munit
    )
  )
  .aggregate(
    unityPay,
    njangi,
    billing,
    coinstar,
    migrantbank,
    wallet,
    revenue,
    `payment-initiation-codegen`,
    `account-information-codegen`
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

lazy val unityPay = (project in file("unity-pay"))
  .settings(
    name := "unity-pay",
    libraryDependencies ++= commonDependencies
  )

lazy val njangi = (project in file("njangi"))
  .settings(
    name := "njangi",
    libraryDependencies ++= commonDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

ThisBuild / dependencyOverrides ++= Seq(
  zioJson
)

lazy val billing = (project in file("billing"))
  .settings(
    name := "billing",
    libraryDependencies ++= commonDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val coinstar = (project in file("coinstar"))
  .settings(
    name := "coinstar",
    libraryDependencies ++= commonDependencies ++ dbDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val migrantbank = (project in file("migrantbank"))
  .settings(
    name := "migrantbank",
    libraryDependencies ++= commonDependencies ++ dbDependencies ++ Seq(
      auth0
    )
  )

lazy val wallet = (project in file("wallet"))
  .settings(
    name := "wallet",
    libraryDependencies ++= commonDependencies ++ dbDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val revenue = (project in file("revenue"))
  .settings(
    name := "revenue",
    libraryDependencies ++= commonDependencies ++ dbDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val `payment-initiation-codegen` =
  (project in file("payment-initiation-codegen"))
    .enablePlugins(OpenApiGeneratorPlugin)
    .settings(
      name := "payment-initiation-codegen",
      // openApiInputSpec := "src/main/resources/swagger.json",
      // openApiGeneratorName := "sclala-sttp-client4",
      openApiModelNamePrefix := "",
      openApiModelNameSuffix := "",
      openApiSkipOverwrite := Some(false),
      openApiRemoveOperationIdPrefix := Some(true),
      openApiGenerateMetadata := SettingDisabled,
      // Use the same JSON so CLI and SBT stay in sync
      openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,
      openApiIgnoreFileOverride := s"${baseDirectory.value.getPath}/openapi-ignore-file",

      // Put generated sources where SBT expects managed sources
      openApiOutputDir := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
      openApiGenerateModelTests := SettingDisabled,
      openApiGenerateApiTests := SettingDisabled,
      openApiValidateSpec := SettingDisabled,
      // Fail fast on bad specs (optional but recommended)
      openApiValidateSpec := Some(true),

      // Wired in as a sourceGenerator, NOT as `compile.dependsOn(generate)`.
      // sbt collects `sources` by globbing src/main/scala in a task separate from
      // `compile`, and dependsOn only sequences generate ahead of `compile` -- not
      // ahead of that glob. So on a clean checkout the glob ran first, found
      // nothing, and codegen compiled 0 sources. A sourceGenerator feeds `sources`
      // directly, so sbt has to run it before compiling.
      Compile / sourceGenerators += Def.task {
        generate.value
        (file(openApiOutputDir.value) ** "*.scala").get()
      }.taskValue,
      // Generated output lands directly in src/main/scala (see openApiOutputDir
      // above), so drop the unmanaged source dir -- otherwise the same files get
      // compiled twice, once via the sourceGenerator and once via the default glob.
      Compile / unmanagedSourceDirectories := Seq.empty,

      // Manual entry point to (re)generate the client without a full compile.
      //
      // Must stay uncached. sbt 2 caches `:=` task results by default, but the
      // cache key is built from the task's `.value` inputs, and nothing here
      // hashes the OpenAPI spec's *contents* -- sbt's own file-input keys
      // (allInputFiles / changedInputFiles) are @transient, i.e. deliberately
      // excluded from cache input. A Def.cachedTask would therefore keep serving
      // a stale client whenever the spec changed, so we always regenerate.
      generate := Def.uncached {
        val _ = openApiGenerate.value

        // Delete the generated build.sbt file so that it is not used for our sbt config
        val buildSbtFile = file(openApiOutputDir.value) / "build.sbt"
        if (buildSbtFile.exists()) {
          buildSbtFile.delete()
        }
      },
      libraryDependencies ++= Seq(
        sttpJsoniter,
        jsoniter,
        jsoniterMacros,
        jsoniterCirce
      ),
      scalacOptions := Seq.empty
    )

lazy val `account-information-codegen` =
  (project in file("account-information-codegen"))
    .enablePlugins(OpenApiGeneratorPlugin)
    .settings(
      name := "account-information-codegen",
      openApiModelNamePrefix := "",
      openApiModelNameSuffix := "",
      openApiSkipOverwrite := Some(false),
      openApiRemoveOperationIdPrefix := Some(true),
      openApiGenerateMetadata := SettingDisabled,
      openApiConfigFile := ((Compile / baseDirectory).value / "config.json").getPath,
      openApiIgnoreFileOverride := s"${baseDirectory.value.getPath}/openapi-ignore-file",
      openApiOutputDir := ((Compile / baseDirectory).value / "src/main/scala").getAbsolutePath,
      openApiGenerateModelTests := SettingDisabled,
      openApiGenerateApiTests := SettingDisabled,
      openApiValidateSpec := SettingDisabled,
      openApiValidateSpec := Some(true),
      Compile / sourceGenerators += Def.task {
        generate.value
        (file(openApiOutputDir.value) ** "*.scala").get()
      }.taskValue,
      Compile / unmanagedSourceDirectories := Seq.empty,
      generate := Def.uncached {
        val _ = openApiGenerate.value

        val buildSbtFile = file(openApiOutputDir.value) / "build.sbt"
        if (buildSbtFile.exists()) {
          buildSbtFile.delete()
        }
      },
      libraryDependencies ++= Seq(
        sttpJsoniter,
        jsoniter,
        jsoniterMacros,
        jsoniterCirce
      ),
      scalacOptions := Seq.empty
    )

Global / onChangedBuildSource := IgnoreSourceChanges
