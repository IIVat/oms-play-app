import com.typesafe.sbt.packager.Keys.daemonUser
import com.typesafe.sbt.packager.Keys.daemonUserUid
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBaseImage

object Settings {

  import sbt.Keys.scalacOptions
  import sbt.Keys._
  import sbt._
  import sbt.util.Level

  val commonSettings = {
    Seq(
      ThisBuild / scalaVersion := "2.13.6",
      scalacOptions := Seq(
        "-Ymacro-annotations",
        "-deprecation",
        "-encoding",
        "utf-8",
        "-explaintypes",
        "-feature",
        "-unchecked",
        "-language:postfixOps",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xcheckinit",
        "-Xfatal-warnings"
      ),
      javacOptions ++= Seq("-g", "-target", "11", "-encoding", "UTF-8"),
      dockerBaseImage := "adoptopenjdk/openjdk11:x86_64-alpine-jre-11.0.6_10",
      Docker / daemonUserUid := None,
      Docker / daemonUser := "daemon",
      logLevel := Level.Info,
      version := (ThisBuild / version).value,
      resolvers ++= Seq(
        Resolver.sbtPluginRepo("releases"),
        Resolver.typesafeRepo("releases"),
        Resolver.mavenCentral,
        Resolver.jcenterRepo
      )
    )
  }

  import Dependencies._

  lazy val assignmentServiceDependencies = Seq(
    akkaHttp,
    circeConfig,
    catsEffect,
    logback,
    logging,
    redis,
    sqsSDK
  ) ++ akka ++ alpakka ++ circe

  lazy val currierServiceDependencies = Seq()
}
