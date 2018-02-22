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

import java.util.UUID

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.libs.json.JsString
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, TestSupport}
import uk.gov.hmrc.http.HttpResponse

class HelpToSaveConnectorImplSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  val host = "host"
  val port = 1

  val connector = new HelpToSaveConnectorImpl(
    Configuration(
      "microservice.services.help-to-save.host" → host,
      "microservice.services.help-to-save.port" → port),
    http
  )

  "The HelpToSaveConnectorImpl" when {

    "creating an account" must {

      "call the correct url and return the response as is" in {
        implicit val createAccountBodyArb: Arbitrary[CreateAccountBody] = Arbitrary(DataGenerators.createAccountBodyGen)
        implicit val correlationIdArb: Arbitrary[UUID] = Arbitrary(Gen.uuid)

        forAll { (body: CreateAccountBody, correlationId: UUID, status: Int, response: String) ⇒
          mockPost(s"http://$host:$port/help-to-save/create-de-account", body, Map("X-CorrelationId" -> correlationId.toString))(Some(HttpResponse(status, Some(JsString(response)))))
          val result = await(connector.createAccount(body, correlationId))
          result.status shouldBe status
          result.json shouldBe JsString(response)
        }
      }

    }

  }

}
