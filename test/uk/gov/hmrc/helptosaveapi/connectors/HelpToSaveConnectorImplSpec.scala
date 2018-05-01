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
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.HttpResponse

// scalastyle:off magic.number
class HelpToSaveConnectorImplSpec extends TestSupport with MockPagerDuty with GeneratorDrivenPropertyChecks with EitherValues {

  val connector = new HelpToSaveConnectorImpl(fakeApplication.configuration, http, mockPagerDuty)

  "The HelpToSaveConnectorImpl" when {

    "creating an account" must {

      "call the correct url and return the response as is" in {
        implicit val createAccountBodyArb: Arbitrary[CreateAccountBody] = Arbitrary(DataGenerators.createAccountBodyGen)
        implicit val correlationIdArb: Arbitrary[UUID] = Arbitrary(Gen.uuid)

        forAll { (body: CreateAccountBody, correlationId: UUID, status: Int, response: String) ⇒
          mockPost("http://localhost:7001/help-to-save/create-de-account", body, Map("X-CorrelationId" -> correlationId.toString))(Some(HttpResponse(status, Some(JsString(response)))))
          val result = await(connector.createAccount(body, correlationId))
          result.status shouldBe status
          result.json shouldBe JsString(response)
        }
      }

    }

    "handling eligibility requests" must {

      val nino = "AE123456C"
      val correlationId = UUID.randomUUID()
      val eligibilityUrl = s"http://localhost:7001/help-to-save/api/eligibility-check/$nino"
      val headers = Map("X-CorrelationId" -> correlationId.toString)

        def eligibilityJson(resultCode: Int, reasonCode: Int) =
          s"""{
           |"result": "eligible",
           |"resultCode": $resultCode,
           |"reason": "receiving UC",
           |"reasonCode": $reasonCode
            }
          """.stripMargin

        def test(eligibilityJson: String, apiResponse: EligibilityResponse) = {
          mockGet(eligibilityUrl, headers)(Some(HttpResponse(200, Some(Json.parse(eligibilityJson)))))
          val result = await(connector.checkEligibility(nino, correlationId))
          result.right.value shouldBe apiResponse
        }

      "handle user eligible response from help to save BE" in {
        test(eligibilityJson(1, 6), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), false))
        test(eligibilityJson(1, 7), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = false), false))
        test(eligibilityJson(1, 8), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), false))
      }

      "handle user InEligible response from help to save BE " in {
        test(eligibilityJson(2, 3), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = false), false))
        test(eligibilityJson(2, 4), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = true), false))
        test(eligibilityJson(2, 5), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = true), false))
        test(eligibilityJson(2, 9), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = false), false))
      }

      "handle AccountAlreadyExists response from help to save BE" in {
        test(eligibilityJson(3, 1), AccountAlreadyExists())
      }

      "handle invalid combination from help to save BE" in {
        mockGet(eligibilityUrl, headers)(Some(HttpResponse(200, Some(Json.parse(eligibilityJson(999, 1111))))))
        mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        val result = await(connector.checkEligibility(nino, correlationId))
        result.isLeft shouldBe true
      }

      "handle json parsing errors" in {
        val invalidEligibilityJson =
          """{
            |"resultCode": 1,
            |"reason": "receiving UC",
            |"reasonCode": 6
            }
          """.stripMargin

        mockGet(eligibilityUrl, headers)(Some(HttpResponse(200, Some(Json.parse(invalidEligibilityJson)))))
        mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        val result = await(connector.checkEligibility(nino, correlationId))
        result.isLeft shouldBe true
      }

      "handle error responses from help-to-save BE" in {
        mockGet(eligibilityUrl, headers)(Some(HttpResponse(500, None)))
        mockPagerDutyAlert("Received unexpected http status in response to eligibility check")
        val result = await(connector.checkEligibility(nino, correlationId))
        result.isLeft shouldBe true
      }
    }

  }

}
