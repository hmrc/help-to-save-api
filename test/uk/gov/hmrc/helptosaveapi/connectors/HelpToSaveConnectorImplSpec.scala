/*
 * Copyright 2024 HM Revenue & Customs
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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnectorImpl.CreateAccountInfo
import uk.gov.hmrc.helptosaveapi.models.ValidateBankDetailsRequest
import uk.gov.hmrc.helptosaveapi.util.DataGenerators.{random, validCreateAccountBodyGen}
import uk.gov.hmrc.helptosaveapi.util.{WireMockMethods, base64Encode}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class HelpToSaveConnectorImplSpec
    extends AnyWordSpec with WireMockMethods with WireMockSupport with GuiceOneAppPerSuite {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      help-to-save {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |""".stripMargin
    )
  )
  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: HelpToSaveConnector = app.injector.instanceOf[HelpToSaveConnectorImpl]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  private val emptyJsonBody = "{}"

  "The HelpToSaveConnectorImpl" when {
    val nino = "AE123456C"
    val systemId = "systemId"
    val correlationId = UUID.randomUUID()
    val headers = Map("X-Correlation-ID" -> correlationId.toString)

    "creating an account" must {

      "call the correct url and return the response as is" in {
        List(
          HttpResponse(200, emptyJsonBody),
          HttpResponse(400, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(403, emptyJsonBody),
          HttpResponse(404, emptyJsonBody),
          HttpResponse(500, emptyJsonBody),
          HttpResponse(502, emptyJsonBody),
          HttpResponse(503, emptyJsonBody)
        ).foreach { httpResponse =>
          val createAccountBody = random(validCreateAccountBodyGen)
          when(
            POST,
            "/help-to-save/create-account",
            Map.empty,
            Map("X-Correlation-ID" -> correlationId.toString),
            Some(Json.toJson(CreateAccountInfo(createAccountBody, 8, "1234")).toString())
          ).thenReturn(httpResponse.status, httpResponse.body)
          val result = await(connector.createAccount(createAccountBody, correlationId, "1234", 8))
          result.status shouldBe httpResponse.status
          result.body shouldBe httpResponse.body
        }
      }
    }

    "handling eligibility requests" must {

      val eligibilityUrl = "/help-to-save/eligibility-check"
      val eligibilityJson = """{"result": "eligible","resultCode": 1,"reason": "receiving UC","reasonCode": 5}"""
      val json = Json.parse(eligibilityJson)

      "call correct url and return the response as is" in {
        val httpResponse = HttpResponse(200, json, Map.empty[String, Seq[String]])
        when(
          GET,
          eligibilityUrl,
          Map("nino" -> nino),
          headers
        ).thenReturn(httpResponse.status, httpResponse.body)
        val result = await(connector.checkEligibility(nino, correlationId))
        result.status shouldBe 200
        result.json shouldBe json
      }
    }

    "handling get account requests" must {

      val getAccountUrl = s"/help-to-save/$nino/account"
      val account =
        """{
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
        val httpResponse = HttpResponse(200, json, Map.empty[String, Seq[String]])
        when(
          method = GET,
          uri = getAccountUrl,
          headers = headers
        ).thenReturn(httpResponse.status, httpResponse.body)
        val result = await(connector.getAccount(nino, systemId, correlationId))
        result.status shouldBe 200
        result.json shouldBe json
      }
    }

    "handling store email requests" must {
      val storeEmailUrl = "/help-to-save/store-email"
      "call the correct url and return the response as is" in {
        val email = base64Encode("email@email.com")
        val httpResponse = HttpResponse(200, "")
        when(
          GET,
          storeEmailUrl,
          Map("email" -> email, "nino" -> nino),
          headers
        ).thenReturn(httpResponse.status, httpResponse.body)
        val result = await(connector.storeEmail(email, nino, correlationId))
        result.status shouldBe 200
      }
    }

    "validating bank details" must {
      "return http response as it is to the caller" in {
        val httpResponse = HttpResponse(200, Json.parse("""{"isValid":true}"""), Map.empty[String, Seq[String]])
        val request = ValidateBankDetailsRequest(nino, "123456", "02012345")
        when(
          POST,
          "/help-to-save/validate-bank-details",
          headers = Map.empty,
          body = Some(Json.toJson(request).toString())
        ).thenReturn(httpResponse.status, httpResponse.body)
        val result = await(connector.validateBankDetails(request))
        result.status shouldBe httpResponse.status
        result.body shouldBe httpResponse.body
      }
    }

    "handling user enrolment status" must {
      "return http response when it calling getUserEnrolmentStatus" in {
        val enrollmentStatusUrl = "/help-to-save/enrolment-status"
        val httpResponse = HttpResponse(200, "")
        when(GET, enrollmentStatusUrl, Map("nino" -> nino), headers).thenReturn(httpResponse.status, httpResponse.body)
        val result = await(connector.getUserEnrolmentStatus(nino, correlationId))
        result.status shouldBe 200
      }
    }
  }
}
