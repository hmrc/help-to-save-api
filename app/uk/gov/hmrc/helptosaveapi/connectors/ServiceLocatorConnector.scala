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

package uk.gov.hmrc.helptosaveapi.connectors

import javax.inject.Inject

import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.models.Registration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[ServiceLocatorConnectorImpl])
trait ServiceLocatorConnector {

  def register(): Future[Boolean]
}

@Singleton
class ServiceLocatorConnectorImpl @Inject() (config:      Configuration,
                                             http:        WSHttp,
                                             environment: Environment) extends ServiceLocatorConnector with ServicesConfig {

  val mode: Mode = environment.mode

  override val runModeConfiguration: Configuration = config

  private val appName: String = getString("appName")
  private val appUrl: String = getString("appUrl")
  private val serviceUrl: String = baseUrl("service-locator")

  val metadata: Option[Map[String, String]] = Some(Map("third-party-api" -> "true"))

  implicit val hc: HeaderCarrier = new HeaderCarrier
  val registration: Registration = Registration(appName, appUrl, metadata)

  def register(): Future[Boolean] = {
    http.post(s"$serviceUrl/registration", registration).map{
      _.status === 204
    } recover {
      case e: Throwable ⇒
        false
    }
  }
}
