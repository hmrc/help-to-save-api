import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    ws,
    hmrc                %% "bootstrap-backend-play-26" % "5.2.0",
    hmrc                %% "mongo-caching"             % "7.0.0-play-26",
    hmrc                %% "simple-reactivemongo"      % "8.0.0-play-26",
    "org.typelevel"     %% "cats-core"                 % "2.0.0",
    "com.github.kxbmap" %% "configs"                   % "0.4.4",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
  )

  val test = Seq(
    hmrc               %% "service-integration-test"    % "1.1.0-play-26"  % "test",
    hmrc               %% "stub-data-generator"         % "0.5.3"          % "test",
    hmrc               %% "reactivemongo-test"          % "4.21.0-play-26" % "test",
    "org.scalamock"    %% "scalamock-scalatest-support" % "3.6.0"          % "test",
    "com.ironcorelabs" %% "cats-scalatest"              % "3.0.0"          % "test"
  )

  // Play 2.6.23 requires akka 2.5.23
  val akka = "com.typesafe.akka"
  val akkaVersion = "2.5.23"
  val overrides = Seq(
    akka %% "akka-stream"    % akkaVersion,
    akka %% "akka-protobuf"  % akkaVersion,
    akka %% "akka-slf4j"     % akkaVersion,
    akka %% "akka-actor"     % akkaVersion,
    akka %% "akka-http-core" % "10.0.15"
  )

}
