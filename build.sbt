name := "rozklad"



scalaVersion := "2.13.8"

idePackagePrefix := Some("rozklad")

libraryDependencies += "co.fs2" %% "fs2-core" % "3.2.11"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.1"
libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC2"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"
libraryDependencies += "com.beachape" %% "enumeratum" % "1.7.0"
libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % Test
libraryDependencies += "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.39.12" % Test
libraryDependencies += "org.postgresql" % "postgresql" % "42.4.1"
libraryDependencies += "org.scalamock" %% "scalamock" % "5.2.0" % Test