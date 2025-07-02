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

package uk.gov.hmrc.helptosaveapi.controllers
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.*
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{credentials, nino as v2Nino}
import uk.gov.hmrc.helptosaveapi.models.*
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountSuccess, RetrievedUserDetails}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.AuthSupport.*
import uk.gov.hmrc.helptosaveapi.util.{AuthSupport, DataGenerators, toFuture}

import java.time.LocalDate

class HelpToSaveControllerSpec extends AuthSupport {

  val apiService: HelpToSaveApiService = mock[HelpToSaveApiService]

  val controller: HelpToSaveController = new HelpToSaveController(apiService, mockAuthConnector, mockCc)

  private def mockCreateAccountPrivileged(request: Request[AnyContent])(
    response: Either[ApiError, CreateAccountSuccess]
  ) =
    when(
      apiService
        .createAccountPrivileged(eqTo(request))(any(), any())
    )
      .thenReturn(toFuture(response))

  private def mockCreateAccountUserRestricted(request: Request[AnyContent], retrievedUserDetails: RetrievedUserDetails)(
    response: Either[ApiError, CreateAccountSuccess]
  ) =
    when(
      apiService
        .createAccountUserRestricted(eqTo(request), eqTo(retrievedUserDetails))(any(), any())
    )
      .thenReturn(toFuture(response))

  private def mockEligibilityCheck(nino: String)(
    response: Either[ApiError, EligibilityResponse]
  ) =
    when(
      apiService
        .checkEligibility(eqTo(nino), any())(any(), any(), any())
    )
      .thenReturn(toFuture(response))

  private def mockGetAccount(nino: String)(
    response: Either[ApiError, Option[Account]]
  ) =
    when(
      apiService
        .getAccount(eqTo(nino))(any(), any(), any())
    )
      .thenReturn(toFuture(response))

  "The CreateAccountController" when {
    val nino = "AE123456C"
    val fakeRequest = FakeRequest()

    val eligibilityResponse: Either[ApiError, ApiEligibilityResponse] =
      Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), accountExists = false))

    "handling createAccount requests" when {

      "the request is made with privileged access" must {

        val privilegedCredentials: Option[Credentials] = Some(Credentials("id", "PrivilegedApplication"))

        "return a Created response if the request is valid and account create is successful " in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Right(CreateAccountSuccess(alreadyHadAccount = false)))

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "return a Conflict response if the request is valid and account create indicates that the account already existed " in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Right(CreateAccountSuccess(alreadyHadAccount = true)))

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CONFLICT
        }

        "handle invalid createAccount requests and return BadRequest" in {
          val error: ApiError = ApiValidationError("invalid request", "uh oh")

          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Left(error))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "return BAD_REQUEST when no JSON is found in request body" in {
          val error: ApiError = ApiValidationError("NO_JSON", "no JSON found in request body")

          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Left(error))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "handle noJson requests to createAccount ApiValidationError" in {
          val error: ApiError = ApiValidationError("NO_JSON", "no JSON found in request body")

          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Left(error))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "handle unexpected internal server errors and return InternalServerError" in {
          val apiBackendError: ApiError = ApiBackendError()

          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Left(apiBackendError))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsJson(result) shouldBe Json.toJson(apiBackendError)
        }

        "handle access errors and return Forbidden" in {
          val apiAccessError: ApiError = ApiAccessError()

          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockCreateAccountPrivileged(fakeRequest)(Left(apiAccessError))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          contentAsJson(result) shouldBe Json.toJson(apiAccessError)
        }
      }

      "the request is made with user-restricted access" must {
        val userInfoRetrievals: Retrieval[
          Option[Name] ~ Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~
            Option[
              String
            ] ~ ConfidenceLevel
        ] =
          v2.Retrievals.name and
            v2.Retrievals.dateOfBirth and
            v2.Retrievals.itmpName and
            v2.Retrievals.itmpDateOfBirth and
            v2.Retrievals.itmpAddress and
            v2.Retrievals.email and
            v2.Retrievals.confidenceLevel

        val createAccountUserDetailsRetrievals = userInfoRetrievals and v2Nino

        def createAccountRetrievalResult(
          u: RetrievedUserDetails
        ): Option[Name] ~ Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~
          Option[
            String
          ] ~ ConfidenceLevel ~ Option[String] = {
          val dob = u.dateOfBirth

          new ~(Some(Name(u.forename, u.surname)), dob) and
            Some(ItmpName(u.forename, None, u.surname)) and dob and
            u.address and u.email and ConfidenceLevel.L200 and u.nino
        }

        def createAccountRetrievalResultLowConfLevel(
          u: RetrievedUserDetails
        ): Option[Name] ~ Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~
          Option[
            String
          ] ~ ConfidenceLevel ~ Option[String] = {
          val dob = u.dateOfBirth

          new ~(Some(Name(u.forename, u.surname)), dob) and
            Some(ItmpName(u.forename, None, u.surname)) and dob and
            u.address and u.email and ConfidenceLevel.L50 and u.nino
        }

        val ggCredentials = Some(Credentials("123-gg", "GovernmentGateway"))

        "return a Created response if the request is valid and account create is successful " in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(
            Right(CreateAccountSuccess(alreadyHadAccount = false))
          )

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "return a Conflict response if the request is valid and account create indicates that the account already existed " in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(
            Right(CreateAccountSuccess(alreadyHadAccount = true))
          )

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CONFLICT
        }

        "prefer the user details from ITMP over GG" in {
          val (userDetailsRetrieval, retrievedUserDetails) = {
            val u = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)

            val retrieval = new ~(Some(Name(Some("a"), Some("b"))), Some(LocalDate.of(1, 2, 3))) and
              Some(ItmpName(Some("c"), None, Some("d"))) and Some(LocalDate.of(3, 2, 1)) and
              u.address and u.email and ConfidenceLevel.L200 and u.nino

            val expectedRetrievedUserDetails =
              u.copy(forename = Some("c"), surname = Some("d"), dateOfBirth = Some(LocalDate.of(3, 2, 1)))
            retrieval -> expectedRetrievedUserDetails
          }

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(
            Right(CreateAccountSuccess(alreadyHadAccount = false))
          )

          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe CREATED
        }

        "handle invalid createAccount requests and return BadRequest" in {
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)
          val error: ApiError = ApiValidationError("invalid request", "uh oh")

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(error))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe Json.toJson(error)
        }

        "handle unexpected internal server errors and return InternalServerError" in {
          val apiBackendError: ApiError = ApiBackendError()
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(apiBackendError))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsJson(result) shouldBe Json.toJson(apiBackendError)
        }

        "handle access errors and return Forbidden" in {
          val apiAccessError: ApiError = ApiAccessError()
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResult(retrievedUserDetails)

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(apiAccessError))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          contentAsJson(result) shouldBe Json.toJson(apiAccessError)
        }
        "handle access errors and return Unauthorised" in {
          val apiAccessError: ApiError = ApiAccessError()
          val retrievedUserDetails = DataGenerators.random(DataGenerators.retrievedUserDetailsGen)
          val userDetailsRetrieval = createAccountRetrievalResultLowConfLevel(retrievedUserDetails)

          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(createAccountUserDetailsRetrievals)(userDetailsRetrieval)
          mockCreateAccountUserRestricted(fakeRequest, retrievedUserDetails)(Left(apiAccessError))

          val result = controller.createAccount()(fakeRequest)

          status(result) shouldBe UNAUTHORIZED
          contentAsString(result) shouldBe "Insufficient confidence level"
        }
      }

      "the request is made with unknown access" must {

        "return a 403" in {
          mockAuthResultWithSuccess(credentials)(Some(Credentials("id", "StandardApplication")))
          val result = controller.createAccount()(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }
      }
    }

    "handling checkEligibility requests" when {

      "the request is made with user-restricted access" must {

        "handle the case when nino from Auth exists but not in the url" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))
          mockEligibilityCheck(nino)(eligibilityResponse)

          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe OK
          contentAsString(
            result
          ) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url exist and they are equal" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))
          mockEligibilityCheck(nino)(eligibilityResponse)

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe OK
          contentAsString(
            result
          ) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url exist and they are NOT equal" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))

          val result = controller.checkEligibility("LX123456D")(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when both ninos from Auth and from url do NOT exist" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(None)

          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when nino from Auth does NOT exist but exist in the url" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(None)

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return BadRequest when a validation error occurs" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))
          mockEligibilityCheck(nino)(Left(ApiValidationError("error")))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return InternalServerError when a backend error occurs" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))
          mockEligibilityCheck(nino)(Left(ApiBackendError()))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle unexpected access errors during eligibility check and return 403" in {
          mockAuthResultWithSuccess(credentials)(ggCredentials)
          mockAuthResultWithSuccess(v2Nino)(Some(nino))
          mockEligibilityCheck(nino)(Left(ApiAccessError()))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }
      }

      "the request is made with privileged access" must {

        "handle the case when nino from Auth exists but not in the url" in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)

          val result = controller.checkEligibilityDeriveNino()(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle the case when nino from Auth does NOT exist but exist in the url" in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockEligibilityCheck(nino)(eligibilityResponse)

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe OK
          contentAsString(
            result
          ) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return BadRequest when a validation error occurs" in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockEligibilityCheck(nino)(Left(ApiValidationError("error")))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe BAD_REQUEST
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle invalid requests and return InternalServerError when a backend error occurs" in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockEligibilityCheck(nino)(Left(ApiBackendError()))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
          headers(result).keys should contain("X-Correlation-ID")
        }

        "handle unexpected access errors during eligibility check and return 403" in {
          mockAuthResultWithSuccess(credentials)(privilegedCredentials)
          mockEligibilityCheck(nino)(Left(ApiAccessError()))

          val result = controller.checkEligibility(nino)(fakeRequest)

          status(result) shouldBe FORBIDDEN
          headers(result).keys should contain("X-Correlation-ID")
        }

      }

      "the request is made with other access" must {
        "return a 403 when no NINO is passed in the URL" in {
          mockAuthResultWithSuccess(credentials)(Some(Credentials("id", "StandardApplication")))
          val result = controller.checkEligibilityDeriveNino()(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }

        "return a 403 a NINO is passed in the URL" in {
          mockAuthResultWithSuccess(credentials)(Some(Credentials("id", "StandardApplication")))
          val result = controller.checkEligibility(nino)(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }
      }
    }

    "handling getAccount requests" must {

      "return a success response along with some json if getting the account is successful" in {
        val bonusTerms = Seq(
          BonusTerm(LocalDate.of(2018, 1, 1), LocalDate.of(2019, 12, 31), BigDecimal("65.43")),
          BonusTerm(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31), BigDecimal("125.43"))
        )

        mockAuthResultWithSuccess(v2Nino)(Some(nino))
        mockGetAccount(nino)(
          Right(
            Some(
              Account(
                accountNumber = "1100000000001",
                headroom = 40.00,
                closed = false,
                blockedFromPayment = false,
                balance = 100.00,
                bonusTerms = bonusTerms
              )
            )
          )
        )

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe OK

        val expectedJson = Json.parse(
          """{"accountNumber":"1100000000001","headroom":40,"closed":false,"blockedFromPayment":false,"balance":100,"bonusTerms":[{"startDate":"20180101","endDate":"20191231","bonusEstimate":65.43},{"startDate":"20200101","endDate":"20211231","bonusEstimate":125.43}]}"""
        )
        val actualJson = Json.parse(contentAsString(result))

        actualJson shouldBe expectedJson
      }

      "return an Internal Server Error when getting an account is unsuccessful" in {
        val apiBackendError: ApiError = ApiBackendError()

        mockAuthResultWithSuccess(v2Nino)(Some(nino))
        mockGetAccount(nino)(Left(apiBackendError))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe Json.toJson(apiBackendError)
      }

      "return a Forbidden when getting an account returns an access error" in {
        val apiAccessError: ApiError = ApiAccessError()

        mockAuthResultWithSuccess(v2Nino)(Some(nino))
        mockGetAccount(nino)(Left(ApiAccessError()))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe FORBIDDEN
        contentAsJson(result) shouldBe Json.toJson(apiAccessError)
      }

      "return a Bad Request when there is a validation error" in {
        val error: ApiError = ApiValidationError("error", "description")

        mockAuthResultWithSuccess(v2Nino)(Some(nino))
        mockGetAccount(nino)(Left(error))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result) shouldBe Json.toJson(error)
      }

      "return a Forbidden result when the nino isn't in Auth" in {
        mockAuthResultWithSuccess(v2Nino)(None)

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe FORBIDDEN
      }

      "return a Not Found result" in {
        mockAuthResultWithSuccess(v2Nino)(Some(nino))
        mockGetAccount(nino)(Right(None))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe NOT_FOUND
      }
    }
  }
}
