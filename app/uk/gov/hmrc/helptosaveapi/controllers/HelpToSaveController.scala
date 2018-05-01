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
import java.util.regex.Matcher

import cats.instances.int._
import cats.syntax.eq._
import cats.syntax.show._
import com.codahale.metrics.Timer
import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountErrorResponse, CreateAccountRequest, EligibilityCheckErrorResponse}
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.{Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class HelpToSaveController @Inject() (helpToSaveConnector: HelpToSaveConnector, metrics: Metrics)(implicit config: Configuration)
  extends BaseController with Logging with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  def createAccount(): Action[AnyContent] = {
    val timer = metrics.apiCreateAccountCallTimer.time()

    httpHeaderValidator.validateHeaderForCreateAccount {
      e ⇒
        updateCreateAccountErrorMetrics(timer)
        BadRequest(CreateAccountErrorResponse("Invalid HTTP headers in request", e).toJson())
    }
      .async { implicit request ⇒
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
  }

  def checkEligibility(nino: String): Action[AnyContent] = {
    val timer = metrics.apiEligibilityCallTimer.time()
    val correlationId = UUID.randomUUID()
    httpHeaderValidator.validateHeaderForEligibilityCheck {
      e ⇒
        metrics.apiEligibilityCallErrorCounter.inc()
        BadRequest(Json.toJson(EligibilityCheckErrorResponse(BAD_REQUEST, s"Invalid HTTP headers in request: $e")))
          .withHeaders(correlationIdHeaderName -> correlationId.toString)
    }.async { implicit request ⇒
      val resultF = if (ninoRegex(nino).matches()) {
        helpToSaveConnector.checkEligibility(nino, correlationId)
          .map {
            r ⇒
              val _ = timer.stop()
              r.fold(
                e ⇒ {
                  logger.warn(s"unexpected error during eligibility check error: $e")
                  metrics.apiEligibilityCallErrorCounter.inc()
                  InternalServerError(Json.toJson(EligibilityCheckErrorResponse(INTERNAL_SERVER_ERROR, "Server Error")))
                },
                elgb ⇒ Ok(Json.toJson(elgb))
              )
          }
      } else {
        metrics.apiEligibilityCallErrorCounter.inc()
        toFuture(BadRequest(Json.toJson(EligibilityCheckErrorResponse(BAD_REQUEST, "NINO doesn't match the regex"))))
      }

      resultF.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
    }
  }

  private val ninoRegex: String ⇒ Matcher = "^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$".r.pattern.matcher _

  private def validateCreateAccountRequest(request: Request[AnyContent], timer: Timer.Context)(f: CreateAccountRequest ⇒ Future[Result]): Future[Result] =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        createAccountRequestValidator.validateRequest(createAccountRequest)
          .fold(
            { errors ⇒
              updateCreateAccountErrorMetrics(timer)
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              BadRequest(CreateAccountErrorResponse("Invalid JSON body in request", errorString).toJson())
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

  @inline
  private def updateCreateAccountErrorMetrics(timer: Timer.Context): Unit = {
    val _ = timer.stop()
    metrics.apiCreateAccountCallErrorCounter.inc()
  }

}

