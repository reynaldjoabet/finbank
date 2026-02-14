import Dependencies._

ThisBuild / scalaVersion     := "3.3.7"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / scalacOptions ++= Seq(
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

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
