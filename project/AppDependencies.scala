import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-30"
  val hmrcBootstrapVersion = "9.0.0"
  val hmrcMongoVersion = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    hmrc                %% s"bootstrap-backend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "org.typelevel"     %% "cats-core"                       % "2.12.0",
    "com.github.kxbmap" %% "configs"                         % "0.6.1"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    hmrc                      %% "stub-data-generator"           % "1.2.0"              % scope,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion     % scope,
    "uk.gov.hmrc"             %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope,
    "org.mockito"             %% "mockito-scala"                 % "1.17.30"            % scope,
    "org.scalatestplus"       %% "scalacheck-1-17"               % "3.2.18.0"           % scope,
    "com.github.tomakehurst"   % "wiremock"                      % "3.0.0-beta-7"       % scope
  )
}
