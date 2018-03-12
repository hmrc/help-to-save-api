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

import cats.instances.int._
import cats.syntax.show._
import cats.syntax.eq._
import com.codahale.metrics.Timer
import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountRequest, ErrorResponse}
import uk.gov.hmrc.helptosaveapi.services.CreateAccountService
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.{Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class HelpToSaveController @Inject() (createAccountService: CreateAccountService, metrics: Metrics)
  extends BaseController with Logging with WithMdcExecutionContext {

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  def createAccount(): Action[AnyContent] = {
    val timer = metrics.apiCallTimer.time()

    httpHeaderValidator.validateHeader{
      e ⇒
        updateErrorMetrics(timer)
        BadRequest(ErrorResponse("Invalid HTTP headers in request", e).toJson())
    }
      .async { implicit request ⇒
        validateRequest(request, timer) {
          case CreateAccountRequest(header, body) ⇒
            logger.info(s"Create Account Request has been made with headers: ${header.show}")
            createAccountService.createAccount(body, header.requestCorrelationId).map { response ⇒
              val _ = timer.stop()
              if (response.status =!= CREATED | response.status =!= CONFLICT) { metrics.apiCallErrorCounter.inc() }
              Option(response.body).fold[Result](Status(response.status))(Status(response.status)(_))
            }
        }
      }
  }

  private def validateRequest(request: Request[AnyContent], timer: Timer.Context)(f: CreateAccountRequest ⇒ Future[Result]): Future[Result] =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        createAccountRequestValidator.validateRequest(createAccountRequest)
          .fold(
            { errors ⇒
              updateErrorMetrics(timer)
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              BadRequest(ErrorResponse("Invalid JSON body in request", errorString).toJson())
            },
            f
          )

      case Some(error: JsError) ⇒
        updateErrorMetrics(timer)
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse JSON in request body: $errorString")
        BadRequest(ErrorResponse("Could not parse JSON in request", errorString).toJson())

      case None ⇒
        updateErrorMetrics(timer)
        logger.warn("No JSON body found in request")
        BadRequest(ErrorResponse("No JSON found in request body", "").toJson())
    }

  @inline
  private def updateErrorMetrics(timer: Timer.Context): Unit = {
    val _ = timer.stop()
    metrics.apiCallErrorCounter.inc()
  }

}

