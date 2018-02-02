/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosaveapi

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import configs.syntax._
import akka.actor.Cancellable
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosaveapi.connectors.ServiceLocatorConnector
import uk.gov.hmrc.helptosaveapi.util.Logging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ApplicationRegistration @Inject() (application:             Application,
                                         applicationLifecycle:    ApplicationLifecycle,
                                         serviceLocatorConnector: ServiceLocatorConnector,
                                         config:                  Configuration)(implicit ec: ExecutionContext) extends Logging {

  val registrationEnabled: Boolean = config.underlying.getBoolean("service-locator-registration.enabled")

  val duration: FiniteDuration = config.underlying.get[FiniteDuration]("service-locator-registration.delay").value

  val cancellable: Cancellable = application.actorSystem.scheduler.scheduleOnce(duration,
    new Runnable {
      override def run(): Unit = if (registrationEnabled) serviceLocatorConnector.register().onComplete{
        case Success(result) ⇒
          logger.info("Service was successfully registered")
        case Failure(e) ⇒
          logger.error(s"Service could not register on the service locator", e)
      }
    })

  applicationLifecycle.addStopHook{ () ⇒
    Future[Unit](
      if (!cancellable.isCancelled) {
        val cancelled = cancellable.cancel()

        if (!cancelled) {
          logger.warn("Could not cancel service locator registration task")
        } else {
          logger.info("Cancelled service locator registration task")
        }
      }
    )
  }

}
