import sbt.*
import sbt.Keys.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(CodeCoverageSettings.settings *)
  .settings(majorVersion := 2)
  .settings(scalaVersion := "2.13.11")
  .settings(PlayKeys.playDefaultPort := 7004)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resources")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
