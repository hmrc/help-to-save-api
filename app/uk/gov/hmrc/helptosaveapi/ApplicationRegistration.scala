package uk.gov.hmrc.helptosaveapi

import javax.inject.Inject
import javax.inject.Singleton

import uk.gov.hmrc.helptosaveapi.config.ServiceConfiguration
import uk.gov.hmrc.helptosaveapi.connectors.ServiceLocatorConnector


@Singleton
class ApplicationRegistration @Inject()(serviceLocatorConnector: ServiceLocatorConnector, config: ServiceConfiguration) {
  val registrationEnabled: Boolean = config.getConfBool("service-locator.enabled", defBool = true)

  if (registrationEnabled) serviceLocatorConnector.register()
}