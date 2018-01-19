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

import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountRequest, ErrorResponse}
import uk.gov.hmrc.helptosaveapi.services.CreateAccountService
import uk.gov.hmrc.helptosaveapi.util.{Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class HelpToSaveController @Inject() (createAccountService: CreateAccountService)
  extends BaseController with Logging with WithMdcExecutionContext {

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  def createAccount(): Action[AnyContent] =
    httpHeaderValidator.validateHeader(e ⇒ BadRequest(ErrorResponse("Invalid HTTP headers in request", e).toJson()))
      .async { implicit request ⇒
        validateRequest(request) {
          case CreateAccountRequest(_, body) ⇒
            createAccountService.createAccount(body).map { response ⇒
              Option(response.body).fold[Result](Status(response.status))(Status(response.status)(_))
            }
        }
      }

  private def validateRequest(request: Request[AnyContent])(f: CreateAccountRequest ⇒ Future[Result]): Future[Result] =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        createAccountRequestValidator.validateRequest(createAccountRequest)
          .fold(
            { errors ⇒
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              BadRequest(ErrorResponse("Invalid JSON body in request", errorString).toJson())
            },
            f
          )

      case Some(error: JsError) ⇒
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse JSON in request body: $errorString")
        BadRequest(ErrorResponse("Could not parse JSON in request", errorString).toJson())

      case None ⇒
        logger.warn("No JSON body found in request")
        BadRequest(ErrorResponse("No JSON found in request body", "").toJson())
    }

}

