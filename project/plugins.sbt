resolvers ++= Seq(
  "sonatype-releases"  at   "https://oss.sonatype.org/content/repositories/releases/")
resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.jcenterRepo

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.0.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.1.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-bobby" % "3.4.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.23" exclude ("org.slf4j", "slf4j-simple"))

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.6.1")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.4")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.9")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.4")

