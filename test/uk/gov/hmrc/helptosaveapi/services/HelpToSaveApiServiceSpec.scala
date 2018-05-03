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

package uk.gov.hmrc.helptosaveapi.services

import java.util.UUID

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import org.scalamock.handlers.{CallHandler1, CallHandler4}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.Http.Status.CREATED
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, MockPagerDuty, TestSupport}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveApiServiceSpec extends TestSupport with MockPagerDuty {

  private val helpToSaveConnector = mock[HelpToSaveConnector]

  val mockApiHttpHeaderValidator: APIHttpHeaderValidator = mock[APIHttpHeaderValidator]

  val mockCreateAccountRequestValidator: CreateAccountRequestValidator = mock[CreateAccountRequestValidator]

  val mockEligibilityRequestValidator: EligibilityRequestValidator = mock[EligibilityRequestValidator]

  def mockCreateAccountHeaderValidator(response: ValidatedNel[String, Request[_]]): CallHandler1[Request[_], ValidatedNel[String, Request[Any]]] =
    (mockApiHttpHeaderValidator.validateHttpHeadersForCreateAccount(_: Request[_])).expects(*).returning(response)

  def mockEligibilityCheckHeaderValidator(response: ValidatedNel[String, Request[_]]): CallHandler1[Request[_], ValidatedNel[String, Request[Any]]] =
    (mockApiHttpHeaderValidator.validateHttpHeadersForEligibilityCheck(_: Request[_])).expects(*).returning(response)

  def mockCreateAccountRequestValidator(request: CreateAccountRequest)(response: Either[String, Unit]): CallHandler1[CreateAccountRequest, ValidatedNel[String, CreateAccountRequest]] =
    (mockCreateAccountRequestValidator.validateRequest(_: CreateAccountRequest))
      .expects(request)
      .returning(fromEither(response).bimap(e ⇒ NonEmptyList.of(e), _ ⇒ request))

  def mockEligibilityCheckRequestValidator(nino: String)(response: ValidatedNel[String, String]): CallHandler1[String, ValidatedNel[String, String]] =
    (mockEligibilityRequestValidator.validateNino(_: String)).expects(nino).returning(response)

  def mockEligibilityCheck(nino: String, correlationId: UUID)(response: Either[String, HttpResponse]): CallHandler4[String, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (helpToSaveConnector.checkEligibility(_: String, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, correlationId, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  def mockCreateAccountService(expectedBody: CreateAccountBody)(response: Either[String, HttpResponse]): CallHandler4[CreateAccountBody, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (helpToSaveConnector.createAccount(_: CreateAccountBody, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedBody, *, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  val service = new HelpToSaveApiServiceImpl(helpToSaveConnector, metrics, mockPagerDuty) {
    override val httpHeaderValidator: APIHttpHeaderValidator = mockApiHttpHeaderValidator
    override val createAccountRequestValidator: CreateAccountRequestValidator = mockCreateAccountRequestValidator
    override val eligibilityRequestValidator: EligibilityRequestValidator = mockEligibilityRequestValidator
  }

  "The HelpToSaveApiService" when {

    "handling CreateAccount requests" must {

      val fakeRequest = FakeRequest()
      val createAccountRequest = DataGenerators.random(DataGenerators.createAccountRequestGen)
      val fakeRequestWithBody = FakeRequest().withJsonBody(Json.toJson(createAccountRequest))

      "handle valid requests and create accounts successfully" in {
        inSequence {
          mockCreateAccountHeaderValidator(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(CREATED, Some(Json.toJson(createAccountRequest.header)))))
        }

        val result = await(service.createAccount(fakeRequestWithBody))
        result shouldBe Right(())
      }

      "handle responses other than 201 from the createAccount endpoint" in {
        inSequence {
          mockCreateAccountHeaderValidator(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(202, Some(Json.toJson(createAccountRequest.header)))))
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }

        val result = await(service.createAccount(fakeRequestWithBody))
        result shouldBe Left(InternalServerErrorResponse())
      }

      "handle unexpected server errors during createAccount" in {
        inSequence {
          mockCreateAccountHeaderValidator(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Left(""))
          mockPagerDutyAlert("Failed to make call to createAccount")
        }

        val result = await(service.createAccount(fakeRequestWithBody))
        result shouldBe Left(InternalServerErrorResponse())
      }

      "return BadRequest for requests with invalid http headers" in {
        inSequence {
          mockCreateAccountHeaderValidator(Invalid(NonEmptyList[String]("content type was not JSON: text/html", Nil)))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
        }

        val result = await(service.createAccount(fakeRequestWithBody))

        result shouldBe Left(CreateAccountErrorResponse("invalid request for CreateAccount", "[content type was not JSON: text/html]"))
      }

      "there is no JSON in the request" in {
        await(service.createAccount(fakeRequest)) shouldBe Left(CreateAccountErrorResponse("No JSON found in request body", ""))
      }

      "the JSON in the request cannot be parsed" in {
        val result = service.createAccount(
          fakeRequest.withJsonBody(Json.parse(
            """
              |{ "key" : "value" }
            """.stripMargin)))

        await(result) shouldBe Left(CreateAccountErrorResponse("Could not parse JSON in request", "/header: [error.path.missing]; /body: [error.path.missing]"))
      }
    }

    "handling eligibility requests" must {

      val nino = "AE123456C"
      val correlationId = UUID.randomUUID()
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

        def eligibilityJson(resultCode: Int, reasonCode: Int) =
          s"""{
           |"result": "eligible",
           |"resultCode": $resultCode,
           |"reason": "receiving UC",
           |"reasonCode": $reasonCode
            }
          """.stripMargin

      "handle valid requests and return EligibilityResponse" in {
        inSequence {
          mockEligibilityCheckHeaderValidator(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(Json.parse(eligibilityJson(1, 6))))))
        }

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Right(ApiEligibilityResponse(Eligibility(true, false, true), false))
      }

      "handle when the request contains invalid headers" in {
        mockEligibilityCheckHeaderValidator(Invalid(NonEmptyList[String]("accept did not contain expected mime type: 'application/vnd.hmrc.1.0+json'", Nil)))
        mockEligibilityCheckRequestValidator(nino)(Valid(nino))

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Left(EligibilityCheckErrorResponse("400", "invalid request for CheckEligibility: NonEmptyList(accept did not contain expected mime type: 'application/vnd.hmrc.1.0+json')"))
      }

      "handle when the request contains invalid nino" in {
        mockEligibilityCheckHeaderValidator(Valid(fakeRequest))
        mockEligibilityCheckRequestValidator(nino)(Invalid(NonEmptyList[String]("NINO doesn't match the regex", Nil)))

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Left(EligibilityCheckErrorResponse("400", "invalid request for CheckEligibility: NonEmptyList(NINO doesn't match the regex)"))
      }

      "handle server errors during eligibility check" in {
        mockEligibilityCheckHeaderValidator(Valid(fakeRequest))
        mockEligibilityCheckRequestValidator(nino)(Valid(nino))
        mockEligibilityCheck(nino, correlationId)(Left("internal server error"))
        mockPagerDutyAlert("Failed to make call to check eligibility")

        val result = await(service.checkEligibility(nino, correlationId))

        result shouldBe Left(InternalServerErrorResponse())
      }

      "transform user Eligible response from help to save BE as expected" in {
        test(eligibilityJson(1, 6), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), false))
        test(eligibilityJson(1, 7), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = false), false))
        test(eligibilityJson(1, 8), ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), false))
      }

      "transform user InEligible response from help to save BE as expected" in {
        test(eligibilityJson(2, 3), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = false), false))
        test(eligibilityJson(2, 4), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = true), false))
        test(eligibilityJson(2, 5), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = true), false))
        test(eligibilityJson(2, 9), ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = false), false))
      }

      "transform AccountAlreadyExists response from help to save BE as expected" in {
        test(eligibilityJson(3, 1), AccountAlreadyExists())
      }

      "handle invalid eligibility result and reason code combination from help to save BE" in {
        inSequence {
          mockEligibilityCheckHeaderValidator(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(Json.parse(eligibilityJson(1, 11))))))
          mockPagerDutyAlert("Failed to make call to check eligibility")

          val result = await(service.checkEligibility(nino, correlationId))
          result shouldBe Left(InternalServerErrorResponse())
        }
      }

        def test(eligibilityJson: String, apiResponse: EligibilityResponse) = {
          mockEligibilityCheckHeaderValidator(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(Json.parse(eligibilityJson)))))

          val result = await(service.checkEligibility(nino, correlationId))
          result shouldBe Right(apiResponse)
        }
    }
  }

}