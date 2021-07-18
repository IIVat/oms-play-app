import sbt._

object Dependencies {

  lazy val circe =
    Seq("io.circe" %% "circe-generic", "io.circe" %% "circe-core", "io.circe" %% "circe-parser")
      .map(_ % Version.circe)

  lazy val akka = Seq(
    "com.typesafe.akka" %% "akka-actor-typed",
    "com.typesafe.akka" %% "akka-stream",
    "com.typesafe.akka" %% "akka-slf4j"
  ).map(_ % Version.akkaVersion)

  lazy val alpakka = Seq("com.lightbend.akka" %% "akka-stream-alpakka-sqs",
                         "com.lightbend.akka" %% "akka-stream-alpakka-sns").map(_ % Version.alpakka)

  lazy val sqsSDK = "software.amazon.awssdk" % "sqs" % Version.awsSdkVersion

  lazy val akkaHttp    = "com.typesafe.akka" %% "akka-http"    % Version.akkaHttp
  lazy val slf4j       = "org.slf4j"         % "slf4j-simple"  % Version.slf4j
  lazy val circeConfig = "io.circe"          %% "circe-config" % Version.circeConfig
  lazy val catsEffect  = "org.typelevel"     %% "cats-effect"  % Version.catsEffect

  //storage
  lazy val redis = "com.github.etaty" %% "rediscala" % Version.redis

  //logging
  lazy val logging = "com.typesafe.scala-logging" %% "scala-logging"  % Version.logging
  lazy val logback = "ch.qos.logback"             % "logback-classic" % Version.logback
}

object Version {
  lazy val akkaHttp        = "10.2.4"
  lazy val awsSdkVersion   = "2.11.14"
  lazy val akkaVersion     = "2.6.15"
  lazy val alpakka         = "3.0.2"
  lazy val slf4j           = "1.7.28"
  lazy val circe           = "0.13.0"
  lazy val cats            = "2.6.1"
  lazy val catsEffect      = "2.5.1"
  lazy val circeConfig     = "0.7.0"
  lazy val logging         = "3.9.2"
  lazy val logback         = "1.2.3"
  lazy val redis           = "1.9.0"
  lazy val logstashVersion = "6.6"

}
