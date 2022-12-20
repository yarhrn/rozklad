import ReleaseTransformations._

Global / onChangedBuildSource := ReloadOnSourceChanges

name := "rozklad"

organization := "com.yarhrn"
homepage := Some(url("https://github.com/yarhrn/rozklad"))
scmInfo := Some(ScmInfo(url("https://github.com/yarhrn/rozklad"), "git@github.com:yarhrn/rozklad.git"))
developers := List(Developer("Yaroslav Hryniuk", "Yaroslav Hryniuk", "yaroslavh.hryniuk@gmail.com", url("https://github.com/yarhrn")))
licenses += ("MIT", url("https://github.com/yarhrn/rozklad/blob/master/LICENSE"))
publishMavenStyle := true

scalaVersion := "2.13.8"

idePackagePrefix.withRank(KeyRanks.Invisible) := Some("rozklad")

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.3"
libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC2"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"
libraryDependencies += "com.beachape" %% "enumeratum" % "1.7.2"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"

libraryDependencies += "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.12" % Test

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.4" % Test
libraryDependencies += "org.postgresql" % "postgresql" % "42.4.3" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % "5.2.0" % Test
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.5" % Test

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies, // : ReleaseStep
  inquireVersions, // : ReleaseStep
  runClean, // : ReleaseStep
  runTest, // : ReleaseStep
  setReleaseVersion, // : ReleaseStep
  commitReleaseVersion, // : ReleaseStep, performs the initial git checks
  tagRelease, // : ReleaseStep
  setNextVersion, // : ReleaseStep
  commitNextVersion, // : ReleaseStep
  pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
)
