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

import play.api.Logger
import com.google.inject.{ImplementedBy, Singleton}
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.models.Registration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@ImplementedBy(classOf[ServiceLocatorConnectorImpl])
trait ServiceLocatorConnector extends ServicesConfig {

  def register(): Future[Boolean]
}

@Singleton
class ServiceLocatorConnectorImpl @Inject() (config: Configuration, http: WSHttp) extends ServiceLocatorConnector {

  private lazy val appName: String = getString("appName")
  private lazy val appUrl: String = getString("appUrl")
  private lazy val serviceUrl: String = baseUrl("service-locator")

  val metadata: Option[Map[String, String]] = Some(Map("third-party-api" -> "true"))

  def register(): Future[Boolean] = {
    implicit val hc: HeaderCarrier = new HeaderCarrier

    val registration = Registration(appName, appUrl, metadata)
    http.POST(s"$serviceUrl/registration", registration, Seq(HeaderNames.CONTENT_TYPE -> ContentTypes.JSON)) map {
      res ⇒
        res.status match {
          case 204 ⇒ true
          case _   ⇒ false
        }
    } recover {
      case e: Throwable ⇒
        false
    }
  }
}
