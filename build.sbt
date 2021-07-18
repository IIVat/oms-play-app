import Settings._

name := "order-management-system"

version := "0.1"

maintainer := "Illia Vatolin <ilya.vatolin@gmail.com>"

lazy val orderAssignmentService = project
  .enablePlugins(AshScriptPlugin, DockerPlugin)
  .settings(commonSettings)
  .settings(dockerExposedPorts := Seq(9060))
  .settings(Compile / mainClass := Some("app.WebServer"))
  .settings(libraryDependencies ++= assignmentServiceDependencies)

lazy val currierService = project
  .settings(commonSettings)
  .settings(dockerExposedPorts := Seq(9060, 9070))
  .settings(libraryDependencies ++= currierServiceDependencies)

lazy val orderManager = project
  .in(file("."))
  .enablePlugins(AshScriptPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(commonSettings)
  .settings(moduleName := "order-manager")
  .settings(name := "order-manager")
  .aggregate(
    currierService,
    orderAssignmentService
  )
