package uk.gov.hmrc.helptosaveapi.config

import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class ServiceConfiguration @Inject()(override val runModeConfiguration: Configuration,
                                     environment: Environment) extends ServicesConfig {
  override protected def mode = environment.mode

  lazy val access = runModeConfiguration.getConfig(s"api.access")
}