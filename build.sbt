ThisBuild / homepage             := Some(url("https://github.com/abs-tudelft/tydi-chisel"))
ThisBuild / organizationHomepage := Some(url("https://github.com/abs-tudelft/"))
ThisBuild / licenses             := List(License.Apache2)
ThisBuild / version              := "0.1.0"
ThisBuild / organization         := "nl.tudelft"
ThisBuild / organizationName     := "ABS Group, Delft University of Technology"
ThisBuild / startYear            := Some(2023)

ThisBuild / scalaVersion := "2.13.12"

val chiselVersion = "5.1.0"

lazy val root = (project in file("."))
  .settings(
    name        := "Tydi-Chisel",
    description := "Tydi-Chisel is an implementation of Tydi concepts in the Chisel HDL.",
    libraryDependencies += "org.chipsalliance" %% "chisel"     % chiselVersion,
    libraryDependencies += "edu.berkeley.cs"   %% "chiseltest" % "5.0.2" % Test,
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations"
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
  )

// Settings required for scalafix
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := scalaBinaryVersion.value

val CICommands =
  Seq("clean", "compile", "test", "scalafmtCheckAll", "scalafmtSbtCheck", "scalafixAll --check").mkString(";")

val PrepareCICommands = Seq("scalafixAll", "scalafmtAll", "scalafmtSbt").mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
