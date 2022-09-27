import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    ws,
    hmrc                %% "bootstrap-backend-play-28" % "5.12.0",
    hmrc                %% "mongo-caching"             % "7.0.0-play-28",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.73.0",
    "org.typelevel"     %% "cats-core"                 % "2.0.0",
    "com.github.kxbmap" %% "configs"                   % "0.4.4",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "1.1.0-play-28"  % "test",
    hmrc                     %% "stub-data-generator"         % "0.5.3"          % "test",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"     % "0.68.0"         % "test",
    "org.scalatest"          %% "scalatest"                   % "3.1.0"          % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"          % "test",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"          % "test",
    "org.scalatestplus"      %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"    % "test",
    "org.scalatestplus"      %% "scalatestplus-mockito"       % "1.0.0-M2"       % "test",
    "org.scalacheck"         %% "scalacheck"                  % "1.15.2"         % "test",
    "com.vladsch.flexmark"     % "flexmark-all"                 % "0.35.10"        % "test"
  )
}
