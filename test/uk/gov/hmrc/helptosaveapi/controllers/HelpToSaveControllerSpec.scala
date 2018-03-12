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

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.scalamock.handlers.{CallHandler1, CallHandler4}
import play.api.libs.json.{JsSuccess, Json}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.services.CreateAccountService
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, TestSupport}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveControllerSpec extends TestSupport {

  val mockHttpHeaderValidator: APIHttpHeaderValidator = mock[APIHttpHeaderValidator]

  val createAccountService: CreateAccountService = mock[CreateAccountService]

  val mockRequestValidator: CreateAccountRequestValidator = mock[CreateAccountRequestValidator]

  val controller: HelpToSaveController = new HelpToSaveController(createAccountService, mockMetrics) {
    override val httpHeaderValidator: APIHttpHeaderValidator = mockHttpHeaderValidator
    override val createAccountRequestValidator: CreateAccountRequestValidator = mockRequestValidator
  }

  def mockHeaderValidator(response: ActionBuilder[Request]): CallHandler1[String ⇒ Result, ActionBuilder[Request]] =
    (mockHttpHeaderValidator.validateHeader(_: String ⇒ Result)).expects(*).returning(response)

  def mockRequestValidator(request: CreateAccountRequest)(response: Either[String, Unit]): CallHandler1[CreateAccountRequest, ValidatedNel[String, CreateAccountRequest]] =
    (mockRequestValidator.validateRequest(_: CreateAccountRequest))
      .expects(request)
      .returning(Validated.fromEither(response).bimap(e ⇒ NonEmptyList.of(e), _ ⇒ request))

  def mockCreateAccountService(expectedBody: CreateAccountBody)(response: Either[String, HttpResponse]): CallHandler4[CreateAccountBody, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (createAccountService.createAccount(_: CreateAccountBody, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedBody, *, *, *)
      .returning(response.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful
      ))

  "The CreateAccountController" must {

    val passingHeaderValidatorResponse: ActionBuilder[Request] = new ActionBuilder[Request] {
      override def invokeBlock[A](request: Request[A], block: Request[A] ⇒ Future[Result]): Future[Result] =
        block(request)
    }

      def headerValidatorResponse(result: Result): ActionBuilder[Request] = new ActionBuilder[Request] {
        override def invokeBlock[A](request: Request[A], block: Request[A] ⇒ Future[Result]): Future[Result] =
          Future.successful(result)
      }

    val createAccountRequest = DataGenerators.random(DataGenerators.createAccountRequestGen)

    "return a Created response if the header validator validates the http headers of the request " +
      "and there is valid JSON in the request" in {
        inSequence{
          mockHeaderValidator(passingHeaderValidatorResponse)
          mockRequestValidator(createAccountRequest)(Right(()))
          // put some dummy JSON in the response to see if it comes out the other end
          mockCreateAccountService(createAccountRequest.body)(Right(HttpResponse(CREATED, Some(Json.toJson(createAccountRequest.header)))))
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(Json.toJson(createAccountRequest)))
        status(result) shouldBe CREATED
        // check we have the dummy JSON we stuck into the response before
        contentAsJson(result).validate[CreateAccountHeader] shouldBe a[JsSuccess[_]]
      }

    "return the response from the header validator if the validator does not validate the http " +
      "headers of the request" in {
        mockHeaderValidator(headerValidatorResponse(InternalServerError))

        await(controller.createAccount()(FakeRequest())) shouldBe InternalServerError
      }

    "return a BadRequest" when {

      "there is no JSON in the request" in {
        mockHeaderValidator(passingHeaderValidatorResponse)

        status(controller.createAccount()(FakeRequest())) shouldBe BAD_REQUEST
      }

      "the JSON in the request cannot be parsed" in {
        mockHeaderValidator(passingHeaderValidatorResponse)

        val result = controller.createAccount()(
          FakeRequest().withBody(Json.parse(
            """
              |{ "key" : "value" }
            """.stripMargin))).run()

        status(result) shouldBe BAD_REQUEST
      }

      "the create account request is invalid" in {
        inSequence {
          mockHeaderValidator(passingHeaderValidatorResponse)
          mockRequestValidator(createAccountRequest)(Left(""))
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(Json.toJson(createAccountRequest)))
        status(result) shouldBe BAD_REQUEST
      }

    }

  }

}
