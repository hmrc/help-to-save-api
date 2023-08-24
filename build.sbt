import sbt.Keys.*
import sbt.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save-api"

val silencerVersion = "1.7.11"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(CodeCoverageSettings.settings *)
  .settings(majorVersion := 2)
  .settings(scalaVersion := "2.12.13")
  .settings(PlayKeys.playDefaultPort := 7004)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resources")
  .settings(scalacOptions += "-P:silencer:pathFilters=routes")
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
