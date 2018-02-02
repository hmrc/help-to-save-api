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

import play.api.{Configuration, Environment}
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.models.Registration
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class ServiceLocatorConnectorImplSpec extends TestSupport {

  val appName: String = "testApp"
  val appUrl: String = "www.test.com"

  val configuration: Configuration = Configuration(
    "microservice.services.service-locator.host" → "localhost",
    "microservice.services.service-locator.port" → 1,
    "appName" → appName,
    "appUrl" → appUrl)

  lazy val connector = new ServiceLocatorConnectorImpl(configuration, http, fakeApplication.injector.instanceOf[Environment])

  val registration = Registration(appName, appUrl, Some(Map("third-party-api" -> "true")))

  "register" must {
    "return a Right() when given a status of 204" in {
      mockPost("http://localhost:1/registration", registration, Map.empty)(Some(HttpResponse(204)))

      val result = connector.register()
      await(result) shouldBe Right(())
    }

    "return a Left when the future fails" ignore {
      mockPost("http://localhost:1/registration", registration, Map.empty)(None)

      val result = connector.register()
      await(result).isLeft shouldBe true
    }

    "return a Left when the HttpResponse is other than 204" in {
      mockPost("http://localhost:1/registration", registration, Map.empty)(Some(HttpResponse(415)))

      val result = connector.register()
      await(result).isLeft shouldBe true
    }
  }

}
