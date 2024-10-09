logLevel := Level.Warn

// Code quality
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("ch.epfl.scala"  % "sbt-scalafix"        % "0.11.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp"             % "2.2.1")
addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
