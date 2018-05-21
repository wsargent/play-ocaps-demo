
name := """play-ocap-demo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayService)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.6"

parallelExecution in Test := true // the default in sbt

resolvers += Resolver.jcenterRepo

libraryDependencies += logback
libraryDependencies += akkaHttpServer
libraryDependencies += filters

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.3.1" % "provided"
libraryDependencies += "com.softwaremill.macwire" %% "util" % "2.3.1"
libraryDependencies += "com.softwaremill.macwire" %% "proxy" % "2.3.1"

libraryDependencies += "org.typelevel" %% "cats-core" % "1.0.1"
libraryDependencies += "org.typelevel" %% "cats-effect" % "0.9"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.196"

//val silhouetteVer = "5.0.4"
//
//// Silhouette config
//lazy val silhouetteLib = Seq(
//  "com.mohiva" %% "play-silhouette" % silhouetteVer,
//  "com.mohiva" %% "play-silhouette-password-bcrypt" % silhouetteVer,
//  "com.mohiva" %% "play-silhouette-crypto-jca" % silhouetteVer,
//  "com.mohiva" %% "play-silhouette-persistence" % silhouetteVer,
//  "com.mohiva" %% "play-silhouette-testkit" % silhouetteVer % "test"
//)
//
//libraryDependencies ++= silhouetteLib