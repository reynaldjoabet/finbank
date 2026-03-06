import Dependencies._

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
  // "-language:strictEquality",
  "-Xmax-inlines:100000"
)

lazy val root = (project in file("."))
  .settings(
    name := "finbank",
    libraryDependencies ++= Seq(
      sttpCore,
      sttpJsoniter,
      http4sBackend,
      `http4s-dsl`,
      emberServer,
      fs2,
      chimney,
      emberClient,
      catsEffect,
      pureconfig,
      slf4j,
      logback,
      scribe,
      scribeSlf4j,
      scribeCats,
      jsoniter,
      jsoniterMacros,
      jsoniterCirce,
      munit,
      http4sCirce,
      circeCore,
      circeGeneric,
      circeParser
    )
  )
  .aggregate(unityPay, njangi, billing, coinstar, migrantbank, wallet, revenue)

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
  nimbusdsOauth2OidcSdk
)

lazy val unityPay = (project in file("unity-pay"))
  .settings(
    name := "unity-pay",
    libraryDependencies ++= commonDependencies
  )

val njangi = (project in file("njangi"))
  .settings(
    name := "njangi",
    libraryDependencies ++= commonDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

ThisBuild / dependencyOverrides ++= Seq(
  zioJson
)

val billing = (project in file("billing"))
  .settings(
    name := "billing",
    libraryDependencies ++= commonDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

val coinstar = (project in file("coinstar"))
  .settings(
    name := "coinstar",
    libraryDependencies ++= commonDependencies ++ Seq(
      quill,
      hikaricp,
      flyway,
      jwtZioJson
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

val migrantbank = (project in file("migrantbank"))
  .settings(
    name := "migrantbank",
    libraryDependencies ++= commonDependencies ++ Seq(
      hikaricp,
      flyway,
      jwtZioJson,
      auth0,
      quill
    )
  )

val wallet = (project in file("wallet"))
  .settings(
    name := "wallet",
    libraryDependencies ++= commonDependencies ++ Seq(
      quill,
      hikaricp,
      flyway,
      jwtZioJson
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val revenue = (project in file("revenue"))
  .settings(
    name := "revenue",
    libraryDependencies ++= commonDependencies ++ Seq(
      quill,
      hikaricp,
      flyway,
      jwtZioJson
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
