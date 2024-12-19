import ReleaseTransformations._

Global / onChangedBuildSource := ReloadOnSourceChanges

name := "rozklad"

organization := "com.yarhrn"
homepage := Some(url("https://github.com/yarhrn/rozklad"))
scmInfo := Some(ScmInfo(url("https://github.com/yarhrn/rozklad"), "git@github.com:yarhrn/rozklad.git"))
developers := List(Developer("Yaroslav Hryniuk", "Yaroslav Hryniuk", "yaroslavh.hryniuk@gmail.com", url("https://github.com/yarhrn")))
licenses += ("MIT", url("https://github.com/yarhrn/rozklad/blob/master/LICENSE"))
publishMavenStyle := true
releaseTagName := s"${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
scalaVersion := "2.13.10"

libraryDependencies += "org.playframework" %% "play-json" % "3.0.4"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5"
libraryDependencies += "com.beachape" %% "enumeratum" % "1.7.4"

libraryDependencies += "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % "6.0.0" % Test
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.14" % Test

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
