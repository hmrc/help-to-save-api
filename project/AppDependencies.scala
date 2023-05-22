import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val hmrcBootstrapVersion = "5.25.0"

  val compile = Seq(
    ws,
    hmrc                %% "bootstrap-backend-play-28" % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.73.0",
    "org.typelevel"     %% "cats-core"                 % "2.8.0",
    "com.github.kxbmap" %% "configs"                   % "0.6.1"
  )

  val test = Seq(
    hmrc                %% "service-integration-test" % "1.3.0-play-28"      % Test,
    hmrc                %% "stub-data-generator"      % "0.5.3"              % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28"  % "0.73.0"             % Test,
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"   % hmrcBootstrapVersion % Test,
    "org.mockito"       %% "mockito-scala"            % "1.17.7"             % Test,
    "org.scalatestplus" %% "scalacheck-1-17"          % "3.2.16.0"           % "test"
  )
}
