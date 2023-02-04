import ReleaseTransformations._

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val scala212 = "2.12.16"
lazy val scala213 = "2.13.6"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalacOptions ++= Seq("-target:jvm-1.8", "-deprecation", "-feature", "-Wunused", "-Ywarn-value-discard")
ThisBuild / scalaVersion := scala213
ThisBuild / organization := "com.yarhrn"
ThisBuild / homepage := Some(url("https://github.com/yarhrn/rozklad"))
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/yarhrn/rozklad"), "git@github.com:yarhrn/rozklad.git"))
ThisBuild / developers := List(Developer("Yaroslav Hryniuk",
  "Yaroslav Hryniuk",
  "yaroslavh.hryniuk@gmail.com",
  url("https://github.com/yarhrn")))
ThisBuild / licenses += ("MIT", url("https://github.com/yarhrn/rozklad/blob/master/LICENSE"))
ThisBuild / publishMavenStyle := true

releaseTagName := s"${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.1"
libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC2"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"
libraryDependencies += "com.beachape" %% "enumeratum" % "1.7.0"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"

libraryDependencies += "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.12" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % Test
libraryDependencies += "org.postgresql" % "postgresql" % "42.4.1" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % "5.2.0" % Test
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.5" % Test


releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)