
name := """play-ocap-demo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayService)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.6"

libraryDependencies += logback
libraryDependencies += akkaHttpServer
libraryDependencies += filters

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.3.1" % "provided"
libraryDependencies += "com.softwaremill.macwire" %% "macrosakka" % "2.3.1" % "provided"
libraryDependencies += "com.softwaremill.macwire" %% "util" % "2.3.1"
libraryDependencies += "com.softwaremill.macwire" %% "proxy" % "2.3.1"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.196"
