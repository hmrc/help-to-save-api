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

package uk.gov.hmrc.helptosaveapi.services

import java.util.UUID

import cats.syntax.cartesian._
import cats.syntax.either._
import cats.syntax.show._
import com.codahale.metrics.Timer
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.OK
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import play.api.mvc.{AnyContent, Request}
import play.mvc.Http.Status
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountRequest, _}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiServiceImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosaveapi.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, PagerDutyAlerting, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveApiServiceImpl])
trait HelpToSaveApiService {

  type CreateAccountResponseType = Future[Either[CreateAccountError, Unit]]
  type CheckEligibilityResponseType = Future[Either[EligibilityCheckError, EligibilityResponse]]

  def createAccount(request: Request[AnyContent])(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType

  def checkEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                          hc: HeaderCarrier,
                                                          ec: ExecutionContext): CheckEligibilityResponseType

}

@Singleton
class HelpToSaveApiServiceImpl @Inject() (helpToSaveConnector: HelpToSaveConnector,
                                          metrics:             Metrics,
                                          pagerDutyAlerting:   PagerDutyAlerting)(implicit config: Configuration,
                                                                                  logMessageTransformer: LogMessageTransformer)
  extends HelpToSaveApiService with Logging {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  val eligibilityRequestValidator: EligibilityRequestValidator = new EligibilityRequestValidator

  override def createAccount(request: Request[AnyContent])(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = {

    val timer = metrics.apiCreateAccountCallTimer.time()

    validateCreateAccountRequest(request, timer) {
      case CreateAccountRequest(header, body) ⇒
        val correlationIdHeader = "requestCorrelationId" -> header.requestCorrelationId.toString
        logger.info(s"Create Account Request has been made with headers: ${header.show}")
        helpToSaveConnector.createAccount(body, header.requestCorrelationId).map[Either[CreateAccountError, Unit]] { response ⇒
          val _ = timer.stop()
          response.status match {
            case Status.CREATED ⇒
              logger.info("successfully created account via API", body.nino, correlationIdHeader)
              Right(())

            case other: Int ⇒
              metrics.apiCreateAccountCallErrorCounter.inc()
              logger.warn(s"Received unexpected http status in response to create account, status=$other", body.nino, correlationIdHeader)
              pagerDutyAlerting.alert("Received unexpected http status in response to create account")
              Left(CreateAccountBackendError())
          }
        }.recover {
          case e ⇒
            val _ = timer.stop()
            metrics.apiCreateAccountCallErrorCounter.inc()
            logger.warn(s"Received unexpected error during create account, error=$e", body.nino, correlationIdHeader)
            pagerDutyAlerting.alert("Failed to make call to createAccount")
            Left(CreateAccountBackendError())
        }
    }
  }

  override def checkEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                                   hc: HeaderCarrier,
                                                                   ec: ExecutionContext): CheckEligibilityResponseType = {
    val timer = metrics.apiEligibilityCallTimer.time()
    val correlationIdHeader = correlationIdHeaderName -> correlationId.toString

    validateCheckEligibilityRequest(nino, correlationIdHeader, timer) {
      _ ⇒
        helpToSaveConnector.checkEligibility(nino, correlationId)
          .map {
            response ⇒
              response.status match {
                case OK ⇒
                  val _ = timer.stop()
                  val result = response.parseJson[EligibilityCheckResponse].flatMap(toApiEligibility)
                  result.fold({
                    e ⇒
                      logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e", nino, correlationIdHeader)
                      pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
                  }, _ ⇒
                    logger.info(s"Call to check eligibility successful, received 200 (OK)", nino, correlationIdHeader)
                  )
                  result.leftMap(e ⇒ EligibilityCheckBackendError())

                case other: Int ⇒
                  metrics.apiEligibilityCallErrorCounter.inc()
                  pagerDutyAlerting.alert("Received unexpected http status in response to eligibility check")
                  Left(EligibilityCheckBackendError())

              }
          }.recover {
            case e ⇒
              metrics.apiEligibilityCallErrorCounter.inc()
              pagerDutyAlerting.alert("Failed to make call to check eligibility")
              Left(EligibilityCheckBackendError())
          }
    }
  }

  private def validateCreateAccountRequest(request: Request[AnyContent], timer: Timer.Context)(f: CreateAccountRequest ⇒ CreateAccountResponseType): CreateAccountResponseType =
    request.body.asJson.map(_.validate[CreateAccountRequest]) match {
      case Some(JsSuccess(createAccountRequest, _)) ⇒
        (httpHeaderValidator.validateHttpHeadersForCreateAccount(request) |@| createAccountRequestValidator.validateRequest(createAccountRequest))
          .map { case (a, b) ⇒ b }
          .fold(
            { errors ⇒
              updateCreateAccountErrorMetrics(timer)
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              toFuture(Left(CreateAccountValidationError("invalid request for CreateAccount", errorString)))
            },
            f
          )

      case Some(error: JsError) ⇒
        updateCreateAccountErrorMetrics(timer)
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse JSON in request body: $errorString")
        Left(CreateAccountValidationError("Could not parse JSON in request", errorString))

      case None ⇒
        updateCreateAccountErrorMetrics(timer)
        logger.warn("No JSON body found in request")
        Left(CreateAccountValidationError("No JSON found in request body", ""))
    }

  private def validateCheckEligibilityRequest(nino:                String,
                                              correlationIdHeader: (String, String),
                                              timer:               Timer.Context)(f: String ⇒ CheckEligibilityResponseType)(implicit request: Request[_]): CheckEligibilityResponseType = {
    (httpHeaderValidator.validateHttpHeadersForEligibilityCheck |@| eligibilityRequestValidator.validateNino(nino))
      .map { case (a, b) ⇒ b }
      .fold(
        e ⇒ {
          logger.warn(s"Could not validate headers: [${e.toList.mkString(",")}]", nino, correlationIdHeader)
          val _ = timer.stop()
          metrics.apiEligibilityCallErrorCounter.inc()
          Left(EligibilityCheckValidationError("400", s"invalid request for CheckEligibility: $e"))
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

  // scalastyle:off magic.number
  private def toApiEligibility(r: EligibilityCheckResponse): Either[String, EligibilityResponse] =
    (r.resultCode, r.reasonCode) match {
      case (1, 6) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), accountExists = false))
      case (1, 7) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = false), accountExists = false))
      case (1, 8) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), accountExists = false))

      case (2, 3) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = false), accountExists = false))
      case (2, 4) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = true), accountExists = false))
      case (2, 5) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = true), accountExists = false))
      case (2, 9) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = false), accountExists = false))

      case (3, _) ⇒ Right(AccountAlreadyExists())
      case _      ⇒ Left(s"invalid combination for eligibility response. Response was '$r'")
    }
}

object HelpToSaveApiServiceImpl {

  private[HelpToSaveApiServiceImpl] case class EligibilityCheckResponse(result: String, resultCode: Int, reason: String, reasonCode: Int)

  private[HelpToSaveApiServiceImpl] object EligibilityCheckResponse {
    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]
  }

}
