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

import org.scalamock.handlers.{CallHandler3, CallHandler5}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.{TestSupport, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class HelpToSaveControllerSpec extends TestSupport {

  val apiService: HelpToSaveApiService = mock[HelpToSaveApiService]

  val controller: HelpToSaveController = new HelpToSaveController(apiService)

  def mockCreateAccount(request: Request[AnyContent])(response: Either[ErrorResponse, Unit]): CallHandler3[Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.CreateAccountResponseType] =
    (apiService.createAccount(_: Request[AnyContent])(_: HeaderCarrier, _: ExecutionContext)).expects(request, *, *).returning(toFuture(response))

  def mockEligibilityCheck(nino: String)(request: Request[AnyContent])(response: Either[ErrorResponse, EligibilityResponse]): CallHandler5[String, UUID, Request[AnyContent], HeaderCarrier, ExecutionContext, apiService.CheckEligibilityResponseType] =
    (apiService.checkEligibility(_: String, _: UUID)(_: Request[AnyContent], _: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *, *, *).returning(toFuture(response))

  "The CreateAccountController" when {

    val fakeRequest = FakeRequest()

    "handling createAccount requests" must {

      "return a Created response if the request is valid and account create is successful " in {
        mockCreateAccount(fakeRequest)(Right(Unit))
        val result = controller.createAccount()(fakeRequest)
        status(result) shouldBe CREATED
      }

      "handle invalid createAccount requests and return BadRequest" in {
        mockCreateAccount(fakeRequest)(Left(CreateAccountErrorResponse("invalid request", "")))
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe """{"errorMessageId":"","errorMessage":"invalid request","errorDetails":""}"""
      }

      "handle unexpected internal server error and return InternalServerError" in {
        mockCreateAccount(fakeRequest)(Left(InternalServerErrorResponse()))
        val result = controller.createAccount()(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe """{"errorMessageId":"","errorMessage":"server error","errorDetails":""}"""
      }
    }

    "handling checkEligibility requests" must {

      val nino = "AE123456C"

      "return a success response if the request is valid and eligibility check is successful " in {
        mockEligibilityCheck(nino)(fakeRequest)(Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), false)))
        val result = controller.checkEligibility(nino)(fakeRequest)
        status(result) shouldBe OK
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle invalid requests and return BadRequest" in {
        mockEligibilityCheck(nino)(fakeRequest)(Left(EligibilityCheckErrorResponse("400", "invalid request")))
        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe """{"code":"400","message":"invalid request"}"""
        headers(result).keys should contain("X-Correlation-ID")
      }

      "handle unexpected internal server error and return InternalServerError" in {
        mockEligibilityCheck(nino)(fakeRequest)(Left(InternalServerErrorResponse()))
        val result = controller.checkEligibility(nino)(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe """{"errorMessageId":"","errorMessage":"server error","errorDetails":""}"""
        headers(result).keys should contain("X-Correlation-ID")
      }
    }
  }
}
