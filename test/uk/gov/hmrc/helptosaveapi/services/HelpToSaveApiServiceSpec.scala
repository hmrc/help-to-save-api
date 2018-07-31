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

import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}
import java.util.UUID

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import org.scalamock.handlers._
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.mvc.Http.Status._
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, MockPagerDuty, TestSupport, base64Encode}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiServiceSpec.CreateAccountRequestOps
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator, EmailValidation}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import uk.gov.hmrc.auth.core.retrieve.ItmpAddress
import uk.gov.hmrc.helptosaveapi.controllers.HelpToSaveController.CreateAccountErrorOldFormat
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountFieldSpec.TestCreateAccountRequest
import uk.gov.hmrc.helptosaveapi.models.createaccount._
import uk.gov.hmrc.helptosaveapi.repo.EligibilityStore

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveApiServiceSpec extends TestSupport with MockPagerDuty {

  private val helpToSaveConnector = mock[HelpToSaveConnector]

  val mockApiHttpHeaderValidator: APIHttpHeaderValidator = mock[APIHttpHeaderValidator]

  val mockCreateAccountRequestValidator: CreateAccountRequestValidator = mock[CreateAccountRequestValidator]

  val mockEligibilityRequestValidator: EligibilityRequestValidator = mock[EligibilityRequestValidator]

  val mockEligibilityStore: EligibilityStore = mock[EligibilityStore]

  def mockCreateAccountHeaderValidator(contentTypeCk: Boolean)(response: ValidatedNel[String, Request[_]]): CallHandler2[Boolean, Request[_], ValidatedNel[String, Request[Any]]] =
    (mockApiHttpHeaderValidator.validateHttpHeaders(_: Boolean)(_: Request[_])).expects(contentTypeCk, *).returning(response)

  def mockEligibilityCheckHeaderValidator(contentTypeCk: Boolean)(response: ValidatedNel[String, Request[_]]): CallHandler2[Boolean, Request[_], ValidatedNel[String, Request[Any]]] =
    (mockApiHttpHeaderValidator.validateHttpHeaders(_: Boolean)(_: Request[_])).expects(contentTypeCk, *).returning(response)

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

  def mockCreateAccountService(expectedBody: CreateAccountBody)(response: Either[String, HttpResponse]): CallHandler6[CreateAccountBody, UUID, String, Int, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (helpToSaveConnector.createAccount(_: CreateAccountBody, _: UUID, _: String, _: Int)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedBody, *, *, *, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  def mockGetAccount(nino: String, systemId: String)(response: Either[String, HttpResponse]): CallHandler5[String, String, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (helpToSaveConnector.getAccount(_: String, _: String, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, systemId, *, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  def mockStoreEmail(encodedEmail: String, nino: String, correlationId: UUID)(response: Either[String, HttpResponse]): CallHandler5[String, String, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (helpToSaveConnector.storeEmail(_: String, _: String, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(encodedEmail, nino, correlationId, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  def mockEligibilityStorePut(correlationId: UUID, eligibility: Eligibility)(response: Either[String, Unit]): CallHandler3[UUID, Eligibility, ExecutionContext, Future[Either[String, Unit]]] = {
    (mockEligibilityStore.put(_: UUID, _: Eligibility)(_: ExecutionContext))
      .expects(correlationId, eligibility, *)
      .returning(Future.successful(response))
  }

  def mockEligibilityStoreGet(correlationId: UUID)(response: Either[String, Option[Eligibility]]): CallHandler2[UUID, ExecutionContext, Future[Either[String, Option[Eligibility]]]] = {
    (mockEligibilityStore.get(_: UUID)(_: ExecutionContext))
      .expects(correlationId, *)
      .returning(Future.successful(response))
  }

  val service = new HelpToSaveApiServiceImpl(helpToSaveConnector, mockMetrics, mockPagerDuty, mockCreateAccountRequestValidator, mockEligibilityStore) {
    override val httpHeaderValidator: APIHttpHeaderValidator = mockApiHttpHeaderValidator
    override val eligibilityRequestValidator: EligibilityRequestValidator = mockEligibilityRequestValidator
  }

  "The HelpToSaveApiService" when {

    val nino = "AE123456C"
    val systemId = "MDTP"
    val correlationId = UUID.randomUUID()
    val validEmail = "test@user.com"

    val fakeRequest = FakeRequest()
    val createAccountRequest = DataGenerators.random(DataGenerators.validCreateAccountRequestGen)
    val fakeRequestWithBody = FakeRequest().withJsonBody(Json.toJson(createAccountRequest))

    "handling user-restricted CreateAccount requests" must {

      val fullRetrievedItmpAddress = ItmpAddress(Some("retrievedLine1"), Some("retrievedLine2"),
                                                 Some("retrievedLine3"), Some("retrievedLine4"), Some("retrievedLine5"), Some("retrievedPostcode"),
                                                 Some("retrievedCountryName"), Some("retrievedCountryCode"))

      val fullRetrievedUserDetails = RetrievedUserDetails(
        Some("retrievedNINO"), Some("retrievedForename"), Some("retrievedSurname"),
        Some(LocalDate.of(1900, 1, 1)), fullRetrievedItmpAddress,
        Some(validEmail))

        def createAccountRequestWithRetrievedDetails(createAccountHeader:     CreateAccountHeader,
                                                     registrationChannel:     String,
                                                     communicationPreference: String) =
          CreateAccountRequest(
            createAccountHeader,
            CreateAccountBody(
              "retrievedNINO",
              "retrievedForename",
              "retrievedSurname",
              LocalDate.of(1900, 1, 1),
              CreateAccountBody.ContactDetails(
                "retrievedLine1",
                "retrievedLine2",
                Some("retrievedLine3"),
                Some("retrievedLine4"),
                Some("retrievedLine5"),
                "retrievedPostcode",
                Some("retrievedCountryCode"),
                communicationPreference,
                None,
                Some(validEmail)
              ),
              registrationChannel
            )
          )

      val createAccountHeader = CreateAccountHeader(
        "version",
        ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("Z")),
        "client",
        correlationId
      )

        def minimalJson(registrationChannel: String): JsValue =
          Json.parse(
            s"""{
             |"header" : ${Json.toJson(createAccountHeader)},
             |"body" : { "registrationChannel" : "$registrationChannel" }
             |}""".stripMargin)

        def minimalJsonRequest(registrationChannel: String) =
          FakeRequest().withJsonBody(minimalJson(registrationChannel))

      "create an account with retrieved details" when {
        val request = createAccountRequest.withRegistrationChannel("online").withCommunicationsPreference("02").withEmail(Some(validEmail))
        val fakeRequestWithBody = FakeRequest().withJsonBody(Json.toJson(request))

        "there are no missing mandatory fields and stores email too" in {

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), request.body.nino, request.header.requestCorrelationId)(Right(HttpResponse(200)))
            mockCreateAccountService(request.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "handles errors during storing email and" in {

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), request.body.nino, request.header.requestCorrelationId)(Left("error string email"))
            mockPagerDutyAlert("could not store email in mongo for the api user")
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiBackendError())
        }

        "handles unexpected status response during storing email" in {

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), request.body.nino, request.header.requestCorrelationId)(Right(HttpResponse(400)))
            mockPagerDutyAlert("unexpected status during storing email for the api user")
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiBackendError())
        }

        "handles the case of missing email when the communicationPreference is set to 02" in {
          val request = createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "02")
          val fakeRequestWithBody = FakeRequest().withJsonBody(Json.toJson(request))

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), request.body.nino, request.header.requestCorrelationId)(Right(HttpResponse(400)))
            mockPagerDutyAlert("unexpected status during storing email for the api user")
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiBackendError())
        }

        "the request has mandatory fields missing and the missing details have been retrieved" in {
          val generatedCreateAccountRequest =
            createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "02")

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), "retrievedNINO", correlationId)(Right(HttpResponse(200)))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"), fullRetrievedUserDetails))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "the email is missing and the email cannot be retrieved but the registration channel is not " +
          "online and the other missing mandatory details can be retrieved" in {
            val generatedCreateAccountRequest =
              createAccountRequestWithRetrievedDetails(createAccountHeader, "callCentre", "00").withEmail(None)

            inSequence {
              mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
              mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
              mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
              mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
            }

            val result = await(service.createAccountUserRestricted(minimalJsonRequest("callCentre"), fullRetrievedUserDetails.copy(email = None)))
            result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
          }

        "the communication preference is missing and the registration channel is online" in {
          val generatedCreateAccountRequest = createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "02")

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, false, true))))
            mockStoreEmail(base64Encode(validEmail), "retrievedNINO", correlationId)(Right(HttpResponse(200)))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"), fullRetrievedUserDetails))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "the communication preference is missing and the registration channel is callCentre" in {
          val generatedCreateAccountRequest =
            createAccountRequestWithRetrievedDetails(createAccountHeader, "callCentre", "00").withEmail(None)

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, false))))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(minimalJsonRequest("callCentre"), fullRetrievedUserDetails))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "the nino is given in the request and the nino can be retrieved and they match" in {
          val generatedCreateAccountRequest =
            createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "02")
              .withEmail(Some(validEmail)).withNINO("nino")

          val request =
            FakeRequest().withJsonBody(
              minimalJson("online").as[JsObject].deepMerge(
                JsObject(List("body" → JsObject(List("nino" → JsString("nino")))))
              ))

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode(validEmail), "nino", correlationId)(Right(HttpResponse(200)))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(request, fullRetrievedUserDetails.copy(nino = Some("nino"))))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "the communicationPreference is given" in {
          val generatedCreateAccountRequest =
            createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "comms")

          val request =
            FakeRequest().withJsonBody(
              minimalJson("online").as[JsObject].deepMerge(
                JsObject(List("body" → JsObject(List("contactDetails" → JsObject(List("communicationPreference" → JsString("comms")))))))
              ))

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(request, fullRetrievedUserDetails))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "the email is given in the request" in {
          val generatedCreateAccountRequest =
            createAccountRequestWithRetrievedDetails(createAccountHeader, "online", "02").withEmail(Some("email"))

          val request =
            FakeRequest().withJsonBody(
              minimalJson("online").as[JsObject].deepMerge(
                JsObject(List("body" → JsObject(List("contactDetails" → JsObject(List("email" → JsString("email")))))))
              ))

          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(FakeRequest()))
            mockCreateAccountRequestValidator(generatedCreateAccountRequest)(Right(()))
            mockEligibilityStoreGet(generatedCreateAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
            mockStoreEmail(base64Encode("email"), "retrievedNINO", correlationId)(Right(HttpResponse(200)))
            mockCreateAccountService(generatedCreateAccountRequest.body)(Right(HttpResponse(CREATED)))
          }

          val result = await(service.createAccountUserRestricted(request, fullRetrievedUserDetails.copy(email = None)))
          result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
        }

        "handles invalid requestCorrelationIds in the request" in {
          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(None))
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiValidationError(s"requestCorrelationId(${request.header.requestCorrelationId}) is not valid"))
        }

        "handles create account requests from non-eligible users" in {
          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Right(Some(Eligibility(false, false, false))))
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiValidationError("invalid api createAccount request, user is not eligible"))
        }

        "handles mongo errors during requestCorrelationId validation" in {
          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(request)(Right(()))
            mockEligibilityStoreGet(request.header.requestCorrelationId)(Left("unknown error"))
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty()))
          result shouldBe Left(ApiBackendError())
        }
      }

      "return a validation error" when {

        "no registration channel is given" in {
          val result = await(
            service.createAccountUserRestricted(
              FakeRequest().withJsonBody(Json.parse(s"""{ "header" : ${Json.toJson(createAccountHeader)} }""".stripMargin)), fullRetrievedUserDetails))

          result shouldBe Left(ApiValidationError("No registration channel was given", ""))
        }

        "there is no JSON in the request" in {
          val result = await(service.createAccountUserRestricted(FakeRequest(), fullRetrievedUserDetails))

          result shouldBe Left(ApiValidationError("NO_JSON", "no JSON found in request body"))
        }

      }

      "return an access error" when {
        "the nino is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"), fullRetrievedUserDetails.copy(nino = None)))
          result shouldBe Left(ApiAccessError())
        }

        "the nino is given in the request and the nino is retrieved but they do not match and there are missing mandatory details" in {
          val request =
            FakeRequest().withJsonBody(
              minimalJson("online").as[JsObject].deepMerge(
                JsObject(List("body" → JsObject(List("nino" → JsString("nino1")))))
              ))

          val result = await(service.createAccountUserRestricted(request, fullRetrievedUserDetails.copy(nino = Some("nino2"))))
          result shouldBe Left(ApiAccessError())
        }

        "the nino is given in the request and the nino is retrieved but they do not match and there are no missing mandatory details" in {
          inSequence {
            mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
            mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
            mockPagerDutyAlert("NINOs in create account request do not match")
          }

          val result = await(service.createAccountUserRestricted(fakeRequestWithBody, RetrievedUserDetails.empty().copy(nino = Some("other-nino"))))
          result shouldBe Left(ApiAccessError())
        }

      }

      "return a backend error" when {

          def checkIsBackendError(result: Either[ApiError, CreateAccountSuccess], field: String) =
            result shouldBe Left(ApiBackendError("MISSING_DATA", s"cannot retrieve data: [$field]"))

        "no email is retrieved when the email is not given in the request and the registration channel is 'online'" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(email = None)))
          checkIsBackendError(result, "Email")
        }

        "the registration channel is not 'online' or 'callCentre' and no communication preference is set" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("other"),
                                                                 fullRetrievedUserDetails.copy(email = None)))
          checkIsBackendError(result, "CommunicationPreference")
        }

        "the forename is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(forename = None)))
          checkIsBackendError(result, "Forename")
        }

        "the surname is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(surname = None)))
          checkIsBackendError(result, "Surname")
        }

        "the date of birth is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(dateOfBirth = None)))

          checkIsBackendError(result, "DateOfBirth")
        }

        "address line 1 is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(address = fullRetrievedItmpAddress.copy(line1 = None))))
          checkIsBackendError(result, "Address")
        }

        "address line 2 is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(address = fullRetrievedItmpAddress.copy(line2 = None))))
          checkIsBackendError(result, "Address")
        }

        "the postcode is not given in the request and cannot be retrieved" in {
          val result = await(service.createAccountUserRestricted(minimalJsonRequest("online"),
                                                                 fullRetrievedUserDetails.copy(address = fullRetrievedItmpAddress.copy(postCode = None))))
          checkIsBackendError(result, "Address")
        }

      }
    }

    "handling privileged create accounts requests" must {

      "handle valid requests and create accounts successfully when all mandatory fields are present and the account creation returns 201" in {
        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(CREATED)))
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = false))
      }

      "handle valid requests and create accounts successfully when all mandatory fields are present and the account creation returns 409" in {
        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(CONFLICT)))
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Right(CreateAccountSuccess(alreadyHadAccount = true))
      }

      "handle 400 responses" in {
        val error = CreateAccountErrorOldFormat("", "", "details")
        val response = HttpResponse(BAD_REQUEST, Some(Json.toJson(error)))

        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Right(response))
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Left(ApiValidationError("details"))
      }

      "handle 400 responses with unrecognised JSON response format" in {
        val response = HttpResponse(BAD_REQUEST, Some(Json.toJson(createAccountRequest.header)))
        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Right(response))
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Left(ApiValidationError("request contained invalid or missing details"))
      }

      "handle responses other than 201 from the createAccount endpoint" in {
        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(202)))
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Left(ApiBackendError())
      }

      "handle unexpected server errors during createAccount" in {
        inSequence {
          mockCreateAccountHeaderValidator(true)(Valid(fakeRequestWithBody))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
          mockEligibilityStoreGet(createAccountRequest.header.requestCorrelationId)(Right(Some(Eligibility(true, true, true))))
          mockCreateAccountService(createAccountRequest.body)(Left(""))
          mockPagerDutyAlert("Failed to make call to createAccount")
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))
        result shouldBe Left(ApiBackendError())
      }

      "return a validation error for requests with invalid http headers" in {
        inSequence {
          mockCreateAccountHeaderValidator(true)(Invalid(NonEmptyList.one("content type was not JSON: text/html")))
          mockCreateAccountRequestValidator(createAccountRequest)(Right(()))
        }

        val result = await(service.createAccountPrivileged(fakeRequestWithBody))

        result shouldBe Left(ApiValidationError("[content type was not JSON: text/html]"))
      }

      "return a validation error if the request has missing mandatory fields" in {
        val resultFuture = service.createAccountPrivileged(
          fakeRequest.withJsonBody(TestCreateAccountRequest.empty().withForename(Some("forename")).toJson))

        await(resultFuture) match {
          case Left(e: ApiValidationError) ⇒ e.code shouldBe "VALIDATION_ERROR"
          case other                       ⇒ fail(s"Expected Left(ApiValidationError) but got $other")
        }
      }

    }

    "handling eligibility requests" must {

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
          mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(Json.parse(eligibilityJson(1, 6))))))
          mockEligibilityStorePut(correlationId, Eligibility(true, false, true))(Right(()))
        }

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Right(ApiEligibilityResponse(Eligibility(true, false, true), false))
      }

      "handle when the request contains invalid headers" in {
        mockEligibilityCheckHeaderValidator(false)(Invalid(NonEmptyList[String]("accept did not contain expected mime type: 'application/vnd.hmrc.1.0+json'", Nil)))
        mockEligibilityCheckRequestValidator(nino)(Valid(nino))

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Left(ApiValidationError("accept did not contain expected mime type: 'application/vnd.hmrc.1.0+json'"))
      }

      "handle when the request contains invalid nino" in {
        mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
        mockEligibilityCheckRequestValidator(nino)(Invalid(NonEmptyList[String]("NINO doesn't match the regex", Nil)))

        val result = await(service.checkEligibility(nino, correlationId))
        result shouldBe Left(ApiValidationError("NINO doesn't match the regex"))
      }

      "handle server errors during eligibility check" in {
        mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
        mockEligibilityCheckRequestValidator(nino)(Valid(nino))
        mockEligibilityCheck(nino, correlationId)(Left("internal server error"))
        mockPagerDutyAlert("Failed to make call to check eligibility")

        val result = await(service.checkEligibility(nino, correlationId))

        result shouldBe Left(ApiBackendError())
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
        val json = Json.parse(eligibilityJson(1, 11))
        inSequence {
          mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(json))))
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")

          val result = await(service.checkEligibility(nino, correlationId))
          result shouldBe Left(ApiBackendError())
        }
      }

        def test(eligibilityJson: String, apiResponse: EligibilityResponse) = {
          mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
          mockEligibilityCheckRequestValidator(nino)(Valid(nino))
          mockEligibilityCheck(nino, correlationId)(Right(HttpResponse(200, Some(Json.parse(eligibilityJson)))))
          apiResponse match {
            case a: ApiEligibilityResponse ⇒ mockEligibilityStorePut(correlationId, a.eligibility)(Right(()))
            case b: AccountAlreadyExists   ⇒
          }
          val result = await(service.checkEligibility(nino, correlationId))
          result shouldBe Right(apiResponse)
        }
    }

    "handling getAccount requests" must {

      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      val account =
        """{
           |"accountNumber":"1100000000001",
           |"isClosed": false,
           |"blocked": {
           |  "unspecified": false
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

      "return OK status and an Account when the call to the connector is successful and there is an account to return" in {
        inSequence {
          mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
          mockGetAccount(nino, systemId)(Right(HttpResponse(200, Some(Json.parse(account)))))
        }

        val result = await(service.getAccount(nino))
        result shouldBe Right(Some(Account("1100000000001", 40.00, false, false, 100.00)))
      }

      "return an Api Error when an INTERNAL SERVER ERROR status is returned from the connector" in {
        inSequence {
          mockEligibilityCheckHeaderValidator(false)(Valid(fakeRequest))
          mockGetAccount(nino, systemId)(Right(HttpResponse(500, None)))
        }

        val result = await(service.getAccount(nino))
        result shouldBe Left(ApiBackendError())
      }

    }
  }

}

object HelpToSaveApiServiceSpec {

  implicit class CreateAccountRequestOps(val r: CreateAccountRequest) extends AnyVal {

    def withRegistrationChannel(channel: String): CreateAccountRequest =
      r.copy(body = r.body.copy(registrationChannel = channel))

    def withEmail(email: Option[String]): CreateAccountRequest =
      r.copy(body = r.body.copy(contactDetails = r.body.contactDetails.copy(email = email)))

    def withCommunicationsPreference(preference: String): CreateAccountRequest =
      r.copy(body = r.body.copy(contactDetails = r.body.contactDetails.copy(communicationPreference = preference)))

    def withNINO(nino: String): CreateAccountRequest =
      r.copy(body = r.body.copy(nino = nino))
  }

}
