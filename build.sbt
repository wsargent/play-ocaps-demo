name := """play-ocap-demo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayService)

scalaVersion := "2.12.6"

parallelExecution in Test := true // the default in sbt

resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("wsargent", "maven")

libraryDependencies += logback
libraryDependencies += akkaHttpServer
libraryDependencies += filters

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.3.1" % "provided"
libraryDependencies += "com.softwaremill.macwire" %% "util" % "2.3.1"
libraryDependencies += "com.softwaremill.macwire" %% "proxy" % "2.3.1"

libraryDependencies += "ocaps" %% "ocaps-core" % "0.1.0"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
