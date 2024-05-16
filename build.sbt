val chiselVersion = "6.3.0"

ThisBuild / scalaVersion := "2.13.12"

// Settings required for scalafix
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := scalaBinaryVersion.value

ThisBuild / developers := List(
  Developer(
    id = "ccromjongh",
    name = "Casper Cromjongh",
    email = "C.Cromjongh@tudelft.nl",
    url = url("https://github.com/abs-tudelft/")
  )
)

ThisBuild / publishMavenStyle := true
//ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / publishTo := Some(Resolver.mavenLocal)

lazy val commonSettings = Seq(
  homepage                                   := Some(url("https://github.com/abs-tudelft/tydi-chisel")),
  organizationHomepage                       := Some(url("https://github.com/abs-tudelft/")),
  licenses                                   := List(License.Apache2),
  version                                    := "0.1.0",
  organization                               := "nl.tudelft",
  organizationName                           := "ABS Group, Delft University of Technology",
  startYear                                  := Some(2023),
  libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion,
  scalacOptions ++= Seq("-language:reflectiveCalls", "-deprecation", "-feature", "-Xcheckinit", "-Ymacro-annotations"),
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
)

lazy val library: Project = (project in file("library"))
  .settings(
    commonSettings,
    name                                     := "Tydi-Chisel",
    description                              := "Tydi-Chisel is an implementation of Tydi concepts in the Chisel HDL.",
    libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0" % Test
  )
//  .dependsOn(testingTools % "test->test")

lazy val testingTools: Project = (project in file("testing"))
  .settings(
    commonSettings,
    name                                     := "Tydi-Chisel-Test",
    description                              := "This package contains the testing tools for Tydi-Chisel",
    libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
  )
  .dependsOn(library % "compile->compile") // Make testingTools project depend on the library project

// Aggregate projects to build them together
lazy val root = (project in file("."))
  .aggregate(library, testingTools)
  .settings(publish / skip := true)

val CICommands =
  Seq("clean", "compile", "test", "scalafmtCheckAll", "scalafmtSbtCheck", "scalafixAll --check").mkString(";")

val PrepareCICommands = Seq("scalafixAll", "scalafmtAll", "scalafmtSbt").mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
