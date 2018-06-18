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
import org.scalatest.EitherValues
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnectorImpl.CreateAccountInfo
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.HttpResponse

// scalastyle:off magic.number
class HelpToSaveConnectorImplSpec extends TestSupport with MockPagerDuty with GeneratorDrivenPropertyChecks with EitherValues {

  val connector = new HelpToSaveConnectorImpl(fakeApplication.configuration, http)

  "The HelpToSaveConnectorImpl" when {

    val nino = "AE123456C"
    val systemId = "systemId"
    val correlationId = UUID.randomUUID()

    "creating an account" must {

      "call the correct url and return the response as is" in {
        implicit val createAccountBodyArb: Arbitrary[CreateAccountBody] = Arbitrary(DataGenerators.createAccountBodyGen)
        implicit val correlationIdArb: Arbitrary[UUID] = Arbitrary(Gen.uuid)

        forAll { (body: CreateAccountBody, correlationId: UUID, clientCode: String, status: Int, response: String) ⇒
          mockPost("http://localhost:7001/help-to-save/create-account", CreateAccountInfo(body, clientCode), Map("X-Correlation-ID" -> correlationId.toString))(Some(HttpResponse(status, Some(JsString(response)))))
          val result = await(connector.createAccount(body, correlationId, clientCode))
          result.status shouldBe status
          result.json shouldBe JsString(response)
        }
      }

    }

    "handling eligibility requests" must {

      val eligibilityUrl = s"http://localhost:7001/help-to-save/api/eligibility-check/$nino"
      val headers = Map("X-Correlation-ID" -> correlationId.toString)

      val eligibilityJson = """{"result": "eligible","resultCode": 1,"reason": "receiving UC","reasonCode": 5}"""
      val json = Json.parse(eligibilityJson)

      "call correct url and return the response as is" in {

        mockGet(eligibilityUrl, headers)(Some(HttpResponse(200, Some(json))))
        val result = await(connector.checkEligibility(nino, correlationId))
        result.status shouldBe 200
        result.json shouldBe json
      }
    }

    "handling get account requests" must {

      val getAccountUrl = s"http://localhost:7001/help-to-save/$nino/account?systemId=$systemId&correlationId=$correlationId"
      val headers = Map("X-Correlation-ID" → correlationId.toString)

      val account =
        s"""{
           |"accountNumber":"1100000000001",
           |"isClosed": false,
           |"blocked": {
           |  "unspecified": true
           |},
           |"balance": "100.00",
           |"paidInThisMonth": "10.00",
           |"canPayInThisMonth": "40.00",
           |"maximumPaidInThisMonth": "50.00",
           |"thisMonthEndDate": "2018-06-30",
           |"bonusTerms": [ {
           |  "bonusEstimate": "50.00",
           |  "bonusPaid": "0.00",
           |  "endDate": "2019-12-31",
           |  "bonusPaidOnOrAfterDate": "2020-01-01"
           |  }
           |],
           |"closureDate": "2022-01-01",
           |"closingBalance": "100.00"
          }""".stripMargin

      val json = Json.parse(account)

      "call the correct url and return the response as is" in {

        mockGet(getAccountUrl, headers)(Some(HttpResponse(200, Some(json))))
        val result = await(connector.getAccount(nino, systemId, correlationId))
        result.status shouldBe 200
        result.json shouldBe json
      }
    }
  }
}
