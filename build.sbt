ThisBuild / name := "Tydi-Chisel"
ThisBuild / description := "Tydi-Chisel is an implementation of Tydi concepts in the Chisel HDL."
ThisBuild / homepage := Some(url("https://github.com/ccromjongh/tydi-chisel"))
ThisBuild / organizationHomepage := Some(url("https://github.com/abs-tudelft/"))
ThisBuild / licenses := List(License.Apache2)
ThisBuild / version := "0.1.0"
ThisBuild / organization := "nl.tudelft"

ThisBuild / scalaVersion := "2.13.12"

val chiselVersion = "5.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "Tydi-Chisel",
    libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion,
    libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "5.0.2",
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := scalaBinaryVersion.value,
  )
)
