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

import java.util.UUID

import org.scalamock.handlers.{CallHandler3, CallHandler4, CallHandler5}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.toFuture
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.helptosaveapi.util.AuthSupport

import scala.concurrent.ExecutionContext

class HelpToSaveControllerSpec extends AuthSupport {

  val apiService: HelpToSaveApiService = mock[HelpToSaveApiService]

  val controller: HelpToSaveController = new HelpToSaveController(apiService, mockAuthConnector)

  def mockCreateAccount(request: Request[AnyContent])(response: Either[CreateAccountError, Unit]): CallHandler3[Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.CreateAccountResponseType] =
    (apiService.createAccount(_: Request[AnyContent])(_: HeaderCarrier, _: ExecutionContext))
      .expects(request, *, *)
      .returning(toFuture(response))

  def mockEligibilityCheck(nino: String)(request: Request[AnyContent])(response: Either[ApiError, EligibilityResponse]): CallHandler5[String, UUID, Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.CheckEligibilityResponseType] =
    (apiService.checkEligibility(_: String, _: UUID)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *, *, *)
      .returning(toFuture(response))

  def mockGetAccount(nino: String)(request: Request[AnyContent])(response: Either[ApiError, Account]): CallHandler4[String, Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.GetAccountResponseType] =
    (apiService.getAccount(_: String)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *, *)
      .returning(toFuture(response))

  "The CreateAccountController" when {

    val nino = "AE123456C"
    val systemId = "systemId"
    val correlationId = UUID.randomUUID()

    val fakeRequest = FakeRequest()

    val eligibilityResponse = Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), false))

    "handling createAccount requests" must {

      "return a Created response if the request is valid and account create is successful " in {
        mockCreateAccount(fakeRequest)(Right(Unit))
        val result = controller.createAccount()(fakeRequest)
        status(result) shouldBe CREATED
      }

      "handle invalid createAccount requests and return BadRequest" in {
        mockCreateAccount(fakeRequest)(Left(CreateAccountValidationError("invalid request", "")))
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe """{"errorMessageId":"","errorMessage":"invalid request","errorDetails":""}"""
      }

      "handle unexpected internal server error and return InternalServerError" in {
        mockCreateAccount(fakeRequest)(Left(CreateAccountBackendError()))
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe """{"errorMessageId":"","errorMessage":"server error","errorDetails":""}"""
      }
    }

    "handling checkEligibility requests" must {

      "handle the case when nino from Auth exists but not in the url and providerType is GovernmentGateway" in {
        mockAuthResultWithSuccess()(retrievals)
        mockEligibilityCheck(nino)(fakeRequest)(eligibilityResponse)

        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth exists but not in the url and providerType is NOT GovernmentGateway" in {
        mockAuthResultWithSuccess()(new ~(Some(nino), Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibility(None)(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url exist and they are equal" in {
        mockAuthResultWithSuccess()(retrievals)
        mockEligibilityCheck(nino)(fakeRequest)(eligibilityResponse)

        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url exist and they are NOT equal" in {
        mockAuthResultWithSuccess()(retrievals)

        val result = controller.checkEligibility(Some("LX123456D"))(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when both ninos from Auth and from url do NOT exist" in {
        mockAuthResultWithSuccess()(new ~(None, Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibility(None)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth does NOT exist but exist in the url and the providerType is PrivilegedApplication" in {
        mockAuthResultWithSuccess()(new ~(None, Credentials("123-id", "PrivilegedApplication")))
        mockEligibilityCheck(nino)(fakeRequest)(eligibilityResponse)

        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"eligibility":{"isEligible":true,"hasWTC":true,"hasUC":true},"accountExists":false}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle the case when nino from Auth does NOT exist but exist in the url and the providerType is NOT PrivilegedApplication" in {
        mockAuthResultWithSuccess()(new ~(None, Credentials("123-id", "foo-bar")))

        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe FORBIDDEN
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle invalid requests and return BadRequest when a validation error occurs" in {
        mockAuthResultWithSuccess()(retrievals)
        mockEligibilityCheck(nino)(fakeRequest)(Left(ApiErrorValidationError("error")))
        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe """{"code":"400","message":"Invalid request, description: error"}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle invalid requests and return InternalServerError when a backend error occurs" in {
        mockAuthResultWithSuccess()(retrievals)
        mockEligibilityCheck(nino)(fakeRequest)(Left(ApiErrorBackendError()))
        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe """{"code":"500","message":"Server error"}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle unexpected internal server error during eligibility check and return 500" in {
        mockAuthResultWithSuccess()(retrievals)
        mockEligibilityCheck(nino)(fakeRequest)(Left(ApiErrorBackendError()))

        val result = controller.checkEligibility(Some(nino))(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe """{"code":"500","message":"Server error"}"""
        headers(result).keys should contain("X-Correlation-ID")
      }
    }

    "handling getAccount requests" must {

      "return a success response along with some json if getting the account is successful" in {
        inSequence{
          mockAuthResultWithSuccess()(retrievals)
          mockGetAccount(nino)(fakeRequest)(Right(Account("1100000000001", 40.00, false)))
        }

        val result = controller.getAccount()(fakeRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe """{"accountNumber":"1100000000001","headroom":40,"closed":false}"""
      }

      "return an Internal Server Error when getting an account is unsuccessful" in {
        inSequence{
          mockAuthResultWithSuccess()(retrievals)
          mockGetAccount(nino)(fakeRequest)(Left(ApiErrorBackendError()))
        }

        val result = controller.getAccount()(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
