import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings.settings :_*)
  .settings(onLoadMessage := "")
  .settings(majorVersion := 2)
  .settings(scalaVersion := "2.13.12")
  .settings(PlayKeys.playDefaultPort := 7004)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resources")
  //silence authprovider warnings - we need to use the deprecated authprovider
  .settings(scalacOptions += "-Wconf:cat=deprecation:silent")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(scalacOptions += "-Wconf:src=txt/.*:s") //silence warning from txt files
  .settings(scalafmtOnCompile := true)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test())
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(Test / testOptions -= Tests.Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oNCHPQR", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
