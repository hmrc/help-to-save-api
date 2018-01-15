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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosaveapi.models.CreateAccountRequest
import uk.gov.hmrc.helptosaveapi.services.{CreateAccountRequestValidator, CreateAccountService, HeaderValidator}
import uk.gov.hmrc.helptosaveapi.util.{Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class CreateAccountController @Inject() (headerValidator:      HeaderValidator,
                                         requestValidator:     CreateAccountRequestValidator,
                                         createAccountService: CreateAccountService)
  extends BaseController with Logging with WithMdcExecutionContext {

  def createAccount(): Action[AnyContent] = headerValidator.validateHeader().async { implicit request ⇒
    validateRequest(request) {
      case CreateAccountRequest(_, body) ⇒
        createAccountService.createAccount(body).map { response ⇒
          Status(response.status)(response.body)
        }
    }
  }

  private def validateRequest(request: Request[AnyContent])(f: CreateAccountRequest ⇒ Future[Result]): Future[Result] =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        requestValidator.validateRequest(createAccountRequest)
          .fold(
            { errors ⇒
              logger.warn(s"Error when validating request header: ${errors.toList.mkString("; ")}")
              BadRequest
            },
            f
          )

      case Some(error: JsError) ⇒
        logger.warn(s"Could not parse JSON in request: ${error.prettyPrint()}")
        BadRequest

      case None ⇒
        logger.warn("No JSON body found in request")
        BadRequest
    }

}
