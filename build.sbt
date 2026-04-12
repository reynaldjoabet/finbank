import Dependencies.*

ThisBuild / scalaVersion := "3.3.7"
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
  "-Xmax-inlines:100000"
)

lazy val root = (project in file("."))
  .settings(
    name := "finbank",
    libraryDependencies ++= commonDependencies ++ Seq(
      circeCore,
      circeGeneric,
      circeParser,
      `http4s-dsl`,
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
  nimbusdsJoseJwt,
  nimbusdsOauth2OidcSdk,
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
      // Compile / sourceGenerators += openApiGenerate.taskValue,
      (Compile / compile) := (Compile / compile).dependsOn(generate).value,
      // (Compile/compile) := ((compile in Compile) dependsOn openApiGenerate).value

      // Define the simple generate command to generate full client codes
      generate := {
        val _ = openApiGenerate.value
        val log = streams.value.log

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
      (Compile / compile) := (Compile / compile).dependsOn(generate).value,
      generate := {
        val _ = openApiGenerate.value
        val log = streams.value.log

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
