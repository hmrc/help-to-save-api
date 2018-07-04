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
import org.scalamock.handlers.{CallHandler3, CallHandler4, CallHandler5}
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
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService.{CheckEligibilityResponseType, CreateAccountResponseType, GetAccountResponseType}
import uk.gov.hmrc.helptosaveapi.util.AuthSupport._
import uk.gov.hmrc.helptosaveapi.util.{AuthSupport, DataGenerators, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class HelpToSaveControllerSpec extends AuthSupport {

  val apiService: HelpToSaveApiService = mock[HelpToSaveApiService]

  val controller: HelpToSaveController = new HelpToSaveController(apiService, mockAuthConnector)

  def mockCreateOrUpdateAccountPrivileged(request: Request[AnyContent])(response: Either[ApiError, CreateAccountSuccess]): CallHandler3[Request[AnyContent], HeaderCarrier, ExecutionContext, CreateAccountResponseType] =
    (apiService.createOrUpdateAccountPrivileged()(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(request, *, *)
      .returning(toFuture(response))

  def mockCreateorUpdateAccountUserRestricted(request: Request[AnyContent], retrievedUserDetails: RetrievedUserDetails)(response: Either[ApiError, CreateAccountSuccess]): CallHandler4[RetrievedUserDetails, Request[AnyContent], HeaderCarrier, ExecutionContext, CreateAccountResponseType] =
    (apiService.createOrUpdateAccountUserRestricted(_: RetrievedUserDetails)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(retrievedUserDetails, request, *, *)
      .returning(toFuture(response))

  def mockEligibilityCheck(nino: String)(response: Either[ApiError, EligibilityResponse]): CallHandler5[String, UUID, Request[AnyContent], HeaderCarrier, ExecutionContext, CheckEligibilityResponseType] =
    (apiService.checkEligibility(_: String, _: UUID)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *, *, *)
      .returning(toFuture(response))

  def mockGetAccount(nino: String)(response: Either[ApiError, Option[Account]]): CallHandler4[String, Request[AnyContent], HeaderCarrier, ExecutionContext, GetAccountResponseType] =
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

    "handling createAccount requests" when {

      "the request is made with privileged access" must {

        val privilegedCredentials = PAClientId("id")

        "return a Created response if the request is valid and account create is successful " in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockCreateOrUpdateAccountPrivileged(fakeRequest)(Right(CreateAccountSuccess(alreadyHadAccount = false)))
          }

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "return a Conflict response if the request is valid and account create indicates that the account already existed " in {

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockCreateOrUpdateAccountPrivileged(fakeRequest)(Right(CreateAccountSuccess(alreadyHadAccount = true)))
          }

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CONFLICT
        }

        "handle invalid createAccount requests and return BadRequest" in {
          val error = ApiValidationError("invalid request", "uh oh")

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockCreateOrUpdateAccountPrivileged(fakeRequest)(Left(error))
          }
          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "handle unexpected internal server errors and return InternalServerError" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockCreateOrUpdateAccountPrivileged(fakeRequest)(Left(ApiBackendError()))
          }
          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsJson(result) shouldBe Json.toJson(ApiBackendError())
        }

        "handle access errors and return Forbidden" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockCreateOrUpdateAccountPrivileged(fakeRequest)(Left(ApiAccessError()))
          }

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          contentAsJson(result) shouldBe Json.toJson(ApiAccessError())
        }

        "change the error JSON format if the API call is v1.0" in {
          val (errorCode, errorMessage) = "CODE" → "message"
          val request = fakeRequest.withHeaders(HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json")

          List[ApiError](
            ApiBackendError(errorCode, errorMessage),
            ApiValidationError(errorCode, errorMessage),
            ApiAccessError(errorCode, errorMessage)
          ).foreach { e ⇒
              inSequence {
                mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
                mockCreateOrUpdateAccountPrivileged(request)(Left(e))
              }

              val result = controller.createAccount()(request)
              contentAsJson(result) shouldBe Json.toJson(CreateAccountErrorOldFormat(errorCode, "error", errorMessage))
            }

        }
      }

      "the request is made with user-restricted access" must {

        val userInfoRetrievals: Retrieval[Name ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Option[String]] =
          Retrievals.name and
            Retrievals.dateOfBirth and
            Retrievals.itmpName and
            Retrievals.itmpDateOfBirth and
            Retrievals.itmpAddress and
            Retrievals.email

        val createAccountUserDetailsRetrievals = userInfoRetrievals and Retrievals.nino

          def createAccountRetrievalResult(u: RetrievedUserDetails): Name ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Option[String] ~ Option[String] = {
            val dob = u.dateOfBirth.map(toJodaDate)

            new ~(Name(u.forename, u.surname), dob) and
              ItmpName(u.forename, None, u.surname) and dob and
              u.address and u.email and u.nino
          }

          def toJodaDate(d: java.time.LocalDate): org.joda.time.LocalDate =
            LocalDate.parse(d.format(DateTimeFormatter.ISO_DATE))

        val ggCredentials = GGCredId("id")

        "return a Created response if the request is valid and account create is successful " in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = false)))
          }

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "return a Conflict response if the request is valid and account create indicates that the account already existed " in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = true)))
          }

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CONFLICT
        }

        "prefer the user details from ITMP over GG" in {
          val (userDetailsRetrieval, retrievedUserDetails) = {
            val u = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)

            val retrieval = new ~(Name(Some("a"), Some("b")), Some(new LocalDate(1, 2, 3))) and
              ItmpName(Some("c"), None, Some("d")) and Some(new LocalDate(3, 2, 1)) and
              u.address and u.email and u.nino

            val expectedRetrievedUserDetails = u.copy(forename    = Some("c"), surname = Some("d"), dateOfBirth = Some(java.time.LocalDate.of(3, 2, 1)))
            retrieval → expectedRetrievedUserDetails
          }

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Right(CreateAccountSuccess(alreadyHadAccount = false)))
          }

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "handle invalid createAccount requests and return BadRequest" in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)
          val error = ApiValidationError("invalid request", "uh oh")

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(error))
          }
          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "handle unexpected internal server errors and return InternalServerError" in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(ApiBackendError()))
          }
          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsJson(result) shouldBe Json.toJson(ApiBackendError())
        }

        "handle access errors and return Forbidden" in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
            mockCreateorUpdateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(ApiAccessError()))
          }

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          contentAsJson(result) shouldBe Json.toJson(ApiAccessError())
        }

        "change the error JSON format if the API call is v1.0" in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)
          val (errorCode, errorMessage) = "CODE" → "message"
          val request = fakeRequest.withHeaders(HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json")

          List[ApiError](
            ApiBackendError(errorCode, errorMessage),
            ApiValidationError(errorCode, errorMessage),
            ApiAccessError(errorCode, errorMessage)
          ).foreach { e ⇒
              inSequence {
                mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
                mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
                mockCreateorUpdateAccountUserRestricted(request, retrievedUserDetails)(Left(e))
              }

              val result = controller.createAccount()(request)
              contentAsJson(result) shouldBe Json.toJson(CreateAccountErrorOldFormat(errorCode, "error", errorMessage))
            }

        }
      }

      "the request is made with unknown access" must {

        "return a 403" in {
          mockAuthResultWithSuccess(Retrievals.authProviderId)(VerifyPid("id"))
          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }
      }

    }

    "handling checkEligibility requests" when {

      "the request is made with user-restricted access" must {

        "handle the case when nino from Auth exists but not in the url" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
            mockEligibilityCheck(nino)(eligibilityResponse)
          }

          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe OK
          contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url exist and they are equal" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
            mockEligibilityCheck(nino)(eligibilityResponse)
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe OK
          contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url exist and they are NOT equal" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
          }

          val result = controller.checkEligibility("LX123456D")(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url do NOT exist" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(None)
          }
          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when nino from Auth does NOT exist but exist in the url" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(None)
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return BadRequest when a validation error occurs" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
            mockEligibilityCheck(nino)(Left(ApiValidationError("error")))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return InternalServerError when a backend error occurs" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
            mockEligibilityCheck(nino)(Left(ApiBackendError()))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle unexpected access errors during eligibility check and return 403" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(ggCredentials)
            mockAuthResultWithSuccess(Retrievals.nino)(Some(nino))
            mockEligibilityCheck(nino)(Left(ApiAccessError()))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }
      }

      "the request is made with privileged access" must {

        "handle the case when nino from Auth exists but not in the url" in {
          mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)

          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when nino from Auth does NOT exist but exist in the url" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockEligibilityCheck(nino)(eligibilityResponse)
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe OK
          contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return BadRequest when a validation error occurs" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockEligibilityCheck(nino)(Left(ApiValidationError("error")))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return InternalServerError when a backend error occurs" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockEligibilityCheck(nino)(Left(ApiBackendError()))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle unexpected access errors during eligibility check and return 403" in {
          inSequence {
            mockAuthResultWithSuccess(Retrievals.authProviderId)(privilegedCredentials)
            mockEligibilityCheck(nino)(Left(ApiAccessError()))
          }

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

      }

      "the request is made with other access" must {

        "return a 403 when no NINO is passed in the URL" in {
          mockAuthResultWithSuccess(Retrievals.authProviderId)(VerifyPid("id"))
          val result = controller.checkEligibilityDeriveNino()(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }

        "return a 403 a NINO is passed in the URL" in {
          mockAuthResultWithSuccess(Retrievals.authProviderId)(VerifyPid("id"))
          val result = controller.checkEligibility(nino)(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }

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
