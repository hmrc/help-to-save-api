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

import cats.instances.int._
import cats.syntax.cartesian._
import cats.syntax.eq._
import cats.syntax.show._
import com.codahale.metrics.Timer
import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountErrorResponse, CreateAccountRequest, EligibilityCheckErrorResponse}
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class HelpToSaveController @Inject() (helpToSaveConnector: HelpToSaveConnector,
                                      metrics:             Metrics)(implicit config: Configuration, logMessageTransformer: LogMessageTransformer)
  extends BaseController with Logging with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  val eligibilityRequestValidator: EligibilityRequestValidator = new EligibilityRequestValidator

  def createAccount(): Action[AnyContent] = Action.async { implicit request ⇒
    val timer = metrics.apiCreateAccountCallTimer.time()
    validateCreateAccountRequest(request, timer) {
      case CreateAccountRequest(header, body) ⇒
        logger.info(s"Create Account Request has been made with headers: ${header.show}")
        helpToSaveConnector.createAccount(body, header.requestCorrelationId).map { response ⇒
          val _ = timer.stop()
          if (response.status =!= CREATED | response.status =!= CONFLICT) {
            metrics.apiCreateAccountCallErrorCounter.inc()
          }
          Option(response.body).fold[Result](Status(response.status))(Status(response.status)(_))
        }
    }
  }

  def checkEligibility(nino: String): Action[AnyContent] = Action.async { implicit request ⇒
    val timer = metrics.apiEligibilityCallTimer.time()
    val correlationId = UUID.randomUUID()
    val correlationIdHeader = correlationIdHeaderName -> correlationId.toString

    validateCheckEligibilityRequest(nino, correlationIdHeader, timer) {
      _ ⇒
        helpToSaveConnector.checkEligibility(nino, correlationId)
          .map {
            r ⇒
              val _ = timer.stop()
              r.fold(
                e ⇒ {
                  logger.warn(s"unexpected error during eligibility check error: $e", nino, correlationIdHeader)
                  metrics.apiEligibilityCallErrorCounter.inc()
                  InternalServerError(toJson(EligibilityCheckErrorResponse("500", "Server Error")))
                },
                elgb ⇒ Ok(toJson(elgb))
              )
          }.map(_.withHeaders(correlationIdHeader))
    }
  }

  private def validateCreateAccountRequest(request: Request[AnyContent], timer: Timer.Context)(f: CreateAccountRequest ⇒ Future[Result]): Future[Result] =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        (httpHeaderValidator.validateHttpHeadersForCreateAccount(request) |@| createAccountRequestValidator.validateRequest(createAccountRequest))
          .map { case (a, b) ⇒ b }
          .fold(
            { errors ⇒
              updateCreateAccountErrorMetrics(timer)
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              BadRequest(CreateAccountErrorResponse("Invalid request for CreateAccount", errorString).toJson())
            },
            f
          )

      case Some(error: JsError) ⇒
        updateCreateAccountErrorMetrics(timer)
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse JSON in request body: $errorString")
        BadRequest(CreateAccountErrorResponse("Could not parse JSON in request", errorString).toJson())

      case None ⇒
        updateCreateAccountErrorMetrics(timer)
        logger.warn("No JSON body found in request")
        BadRequest(CreateAccountErrorResponse("No JSON found in request body", "").toJson())
    }

  private def validateCheckEligibilityRequest(nino:                String,
                                              correlationIdHeader: (String, String),
                                              timer:               Timer.Context)(f: String ⇒ Future[Result])(implicit request: Request[_]): Future[Result] = {
    (httpHeaderValidator.validateHttpHeadersForEligibilityCheck |@| eligibilityRequestValidator.validateNino(nino))
      .map { case (a, b) ⇒ b }
      .fold(
        e ⇒ {
          logger.warn(s"Could not validate headers: [${e.toList.mkString(",")}]")
          val _ = timer.stop()
          metrics.apiEligibilityCallErrorCounter.inc()
          BadRequest(toJson(EligibilityCheckErrorResponse("400", s"Invalid request for CheckEligibility: $e")))
            .withHeaders(correlationIdHeader)
        }, {
          f
        }
      )
  }

  @inline
  private def updateCreateAccountErrorMetrics(timer: Timer.Context): Unit = {
    val _ = timer.stop()
    metrics.apiCreateAccountCallErrorCounter.inc()
  }

}

