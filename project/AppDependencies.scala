import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {
  val hmrc = "uk.gov.hmrc"
  val hmrcBootstrapVersion = "7.23.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    hmrc                %% "bootstrap-backend-play-28" % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.73.0",
    "org.typelevel"     %% "cats-core"                 % "2.9.0",
    "com.github.kxbmap" %% "configs"                   % "0.6.1"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    hmrc                %% "stub-data-generator"     % "1.1.0"              % scope,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.73.0"             % scope,
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % hmrcBootstrapVersion % scope,
    "org.mockito"       %% "mockito-scala"           % "1.17.12"            % scope,
    "org.scalatestplus" %% "scalacheck-1-17"         % "3.2.16.0"           % scope
  )
}
