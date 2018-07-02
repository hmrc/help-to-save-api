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

package uk.gov.hmrc.helptosaveapi.controllers

import java.time.format.DateTimeFormatter
import java.util.UUID

import org.joda.time.LocalDate
import org.scalamock.handlers.{CallHandler4, CallHandler5}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosaveapi.controllers.HelpToSaveController.CreateAccountErrorOldFormat
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountSuccess, RetrievedUserDetails}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.AuthSupport._
import uk.gov.hmrc.helptosaveapi.util.{AuthSupport, DataGenerators, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class HelpToSaveControllerSpec extends AuthSupport {

  val apiService: HelpToSaveApiService = mock[HelpToSaveApiService]

  val controller: HelpToSaveController = new HelpToSaveController(apiService, mockAuthConnector)

  def mockCreateAccount(request: Request[AnyContent], credentials: Credentials, retrievedUserDetails: RetrievedUserDetails)(response: Either[ApiError, CreateAccountSuccess]): CallHandler5[Request[AnyContent], Credentials, RetrievedUserDetails, HeaderCarrier, ExecutionContext, apiService.CreateAccountResponseType] =
    (apiService.createAccount(_: Request[AnyContent], _: Credentials, _: RetrievedUserDetails)(_: HeaderCarrier, _: ExecutionContext))
      .expects(request, credentials, retrievedUserDetails, *, *)
      .returning(toFuture(response))

  def mockEligibilityCheck(nino: String)(response: Either[ApiError, EligibilityResponse]): CallHandler5[String, UUID, Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.CheckEligibilityResponseType] =
    (apiService.checkEligibility(_: String, _: UUID)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *, *, *)
      .returning(toFuture(response))

  def mockGetAccount(nino: String)(response: Either[ApiError, Option[Account]]): CallHandler4[String, Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.GetAccountResponseType] =
    (apiService.getAccount(_: String)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *, *)
      .returning(toFuture(response))

  "The CreateAccountController" when {

    val nino = "AE123456C"
    val systemId = "systemId"
    val correlationId = UUID.randomUUID()

    val fakeRequest = FakeRequest()

    val eligibilityResponse: Either[ApiError, ApiEligibilityResponse] =
      Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), false))

    "handling createAccount requests" must {

      val userInfoRetrievals: Retrieval[Name ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Option[String]] =
        Retrievals.name and
          Retrievals.dateOfBirth and
          Retrievals.itmpName and
          Retrievals.itmpDateOfBirth and
          Retrievals.itmpAddress and
          Retrievals.email

      val createAccountRetrievals =
        userInfoRetrievals and Retrievals.nino and Retrievals.credentials

      val credentials = Credentials("id", "type")

        def createAccountRetrievalResult(u: RetrievedUserDetails): Name ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Option[String] ~ Option[String] ~ Credentials = {
          val dob = u.dateOfBirth.map(toJoddaDate)

          new ~(Name(u.forename, u.surname), dob) and
            ItmpName(u.forename, None, u.surname) and dob and
            u.address and u.email and u.nino and credentials
        }

        def toJoddaDate(d: java.time.LocalDate): org.joda.time.LocalDate =
          LocalDate.parse(d.format(DateTimeFormatter.ISO_DATE))

      "return a Created response if the request is valid and account create is successful " in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = false)))
        }

        val result = controller.createAccount()(fakeRequest)
        status(result) shouldBe CREATED
      }

      "return a Conflict response if the request is valid and account create indicates that the accoutn already existed " in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = true)))
        }

        val result = controller.createAccount()(fakeRequest)
        status(result) shouldBe CONFLICT
      }

      "prefer the user details from ITMP over GG" in {
        val (retrieval, retrievedUserDetails) = {
          val u = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)

          val retrieval = new ~(Name(Some("a"), Some("b")), Some(new LocalDate(1, 2, 3))) and
            ItmpName(Some("c"), None, Some("d")) and Some(new LocalDate(3, 2, 1)) and
            u.address and u.email and u.nino and credentials

          val expectedRetrievedUserDetails = u.copy(forename    = Some("c"), surname = Some("d"), dateOfBirth = Some(java.time.LocalDate.of(3, 2, 1)))
          retrieval → expectedRetrievedUserDetails
        }

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = false)))
        }

        val result = controller.createAccount()(fakeRequest)
        status(result) shouldBe CREATED
      }

      "handle invalid createAccount requests and return BadRequest" in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)
        val error = ApiValidationError("invalid request", "uh oh")

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Left(error))
        }
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        contentAsJson(result) shouldBe Json.toJson(error)
      }

      "handle unexpected internal server errors and return InternalServerError" in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Left(ApiBackendError()))
        }
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe Json.toJson(ApiBackendError())
      }

      "handle access errors and return Forbidden" in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)

        inSequence {
          mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
          mockCreateAccount(fakeRequest, credentials, retrievedUserDetails)(Left(ApiAccessError()))
        }

        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe FORBIDDEN
        contentAsJson(result) shouldBe Json.toJson(ApiAccessError())
      }

      "change the error JSON format if the API call is v1.0" in {
        val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
        val retrieval = createAccountRetrievalResult(retrievedUserDetails)
        val (errorCode, errorMessage) = "CODE" → "message"
        val request = fakeRequest.withHeaders(HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json")

        List[ApiError](
          ApiBackendError(errorCode, errorMessage),
          ApiValidationError(errorCode, errorMessage),
          ApiAccessError(errorCode, errorMessage)
        ).foreach{ e ⇒
            inSequence {
              mockAuthResultWithSuccess(createAccountRetrievals)(retrieval)
              mockCreateAccount(request, credentials, retrievedUserDetails)(Left(e))
            }

            val result = controller.createAccount()(request)
            contentAsJson(result) shouldBe Json.toJson(CreateAccountErrorOldFormat(errorCode, "error", errorMessage))
          }

      }

    }

    "handling checkEligibility requests" must {

      "handle the case when nino from Auth exists but not in the url and providerType is GovernmentGateway" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(eligibilityResponse)

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth exists but not in the url and providerType is NOT GovernmentGateway" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(new ~(Some(nino), Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibilityDeriveNino()(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url exist and they are equal" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(eligibilityResponse)

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url exist and they are NOT equal" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)

        val result = controller.checkEligibility("LX123456D")(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url do NOT exist and the authprovider is not GG" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(new ~(None, Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibilityDeriveNino()(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url do NOT exist and the authprovider is GG" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(new ~(None, Credentials("GG", "GovernmentGateway")))

        val result = controller.checkEligibilityDeriveNino()(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth does NOT exist but exist in the url and the providerType is PrivilegedApplication" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(new ~(None, Credentials("123-id", "PrivilegedApplication")))
        mockEligibilityCheck(nino)(eligibilityResponse)

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth does NOT exist but exist in the url and the providerType is NOT PrivilegedApplication" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(new ~(None, Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle invalid requests and return BadRequest when a validation error occurs" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(Left(ApiValidationError("error")))
        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle invalid requests and return InternalServerError when a backend error occurs" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(Left(ApiBackendError()))
        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle unexpected internal server error during eligibility check and return 500" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(Left(ApiBackendError()))

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle unexpected access errors during eligibility check and return 403" in {
        mockAuthResultWithSuccess(Retrievals.nino and Retrievals.credentials)(retrievals)
        mockEligibilityCheck(nino)(Left(ApiAccessError()))

        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }
    }

    "handling getAccount requests" must {

      "return a success response along with some json if getting the account is successful" in {
        inSequence{
          mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          mockGetAccount(nino)(Right(Some(Account("1100000000001", 40.00, false))))
        }

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"accountNumber":"1100000000001","headroom":40,"closed":false}"""
      }

      "return an Internal Server Error when getting an account is unsuccessful" in {
        inSequence{
          mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          mockGetAccount(nino)(Left(ApiBackendError()))
        }

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe Json.toJson(ApiBackendError())
      }

      "return a Forbidden when getting an account returns an access error" in {
        inSequence{
          mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          mockGetAccount(nino)(Left(ApiAccessError()))
        }

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe FORBIDDEN
        contentAsJson(result) shouldBe Json.toJson(ApiAccessError())
      }

      "return a Bad Request when there is a validation error" in {
        val error = ApiValidationError("error", "description")
        inSequence{
          mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          mockGetAccount(nino)(Left(error))
        }

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result) shouldBe Json.toJson(error)
      }

      "return a Forbidden result when the nino isn't in Auth" in {
        mockAuthResultWithSuccess(Retrievals.nino)(None)

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe FORBIDDEN
      }

      "return a Not Found result" in {
        inSequence{
          mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          mockGetAccount(nino)(Right(None))
        }

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe NOT_FOUND
      }

    }
  }
}
