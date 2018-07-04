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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.show._
import com.codahale.metrics.{Counter, Timer}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request}
import play.mvc.Http.Status
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.controllers.HelpToSaveController.CreateAccountErrorOldFormat
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.createaccount._
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService.{CheckEligibilityResponseType, CreateAccountResponseType, GetAccountResponseType}
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

  def createOrUpdateAccountPrivileged()(implicit r: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType

  def createOrUpdateAccountUserRestricted(retrievedUserDetails: RetrievedUserDetails)(implicit r: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType

  def checkEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                          hc: HeaderCarrier,
                                                          ec: ExecutionContext): CheckEligibilityResponseType

  def getAccount(nino: String)(implicit request: Request[AnyContent], hc: HeaderCarrier,
                               ec: ExecutionContext): GetAccountResponseType

}

object HelpToSaveApiService {
  type CreateAccountResponseType = Future[Either[ApiError, CreateAccountSuccess]]
  type CheckEligibilityResponseType = Future[Either[ApiError, EligibilityResponse]]
  type GetAccountResponseType = Future[Either[ApiError, Option[Account]]]
}

@Singleton
class HelpToSaveApiServiceImpl @Inject() (helpToSaveConnector: HelpToSaveConnector,
                                          metrics:             Metrics,
                                          pagerDutyAlerting:   PagerDutyAlerting)(implicit config: Configuration,
                                                                                  logMessageTransformer: LogMessageTransformer)
  extends HelpToSaveApiService with CreateAccountBehaviour with Logging {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  val eligibilityRequestValidator: EligibilityRequestValidator = new EligibilityRequestValidator

  val systemId: String = config.underlying.getString("system-id")

  override def createOrUpdateAccountUserRestricted(retrievedUserDetails: RetrievedUserDetails)(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = { //scalastyle:ignore

    val timer = metrics.apiCreateAccountUserRestrictedCallTimer.time()
    val result = request.body.asJson.fold[CreateAccountResponseType] {
      Future.successful(Left(ApiValidationError("NO_JSON", "no JSON found in request body")))
    }{ json ⇒
      val missingMandatoryFields = CreateAccountField.missingMandatoryFields(json)

      if (missingMandatoryFields.isEmpty) {
        createOrUpdateAccount(json, retrievedUserDetails.nino)
      } else {
        (for {
          updatedJson ← EitherT.fromEither[Future](fillInMissingDetailsGG(json, missingMandatoryFields, retrievedUserDetails))
          r ← EitherT(createOrUpdateAccount(updatedJson, retrievedUserDetails.nino))
        } yield r
        ).value
      }
    }

    val _ = timer.stop()
    result.map(_.leftMap{ e ⇒
      metrics.apiCreateAccountUserRestrictedCallErrorCounter.inc()
      e
    })
  }

  override def createOrUpdateAccountPrivileged()(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = { //scalastyle:ignore
    val timer = metrics.apiCreateAccountPrivilegedCallTimer.time()

    val result = request.body.asJson.fold[CreateAccountResponseType](
      Future.successful(Left(ApiValidationError("NO_JSON", "no JSON found in request body")))
    ){ createOrUpdateAccount(_, None) }

    val _ = timer.stop()
    result.map(_.leftMap { e ⇒
      metrics.apiCreateAccountPrivilegedCallErrorCounter.inc()
      e
    })
  }

  private def createOrUpdateAccount(json:          JsValue,
                                    retrievedNINO: Option[String])(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType =
    validateCreateAccountRequest(json, request) {
      case createAccountRequest @ CreateAccountRequest(header, body) ⇒
        logger.info(s"Create Account Request has been made with headers: ${header.show}")

        if (retrievedNINO.forall(_ === body.nino)) {
          val result = for {
            maybeAccount ← EitherT(getAccount(body.nino, header.requestCorrelationId))
            r ← EitherT(maybeAccount.fold[CreateAccountResponseType](
              Left(ApiValidationError("updates are not yet supported")))(_ ⇒ createAccount(createAccountRequest))
            )
          } yield r

          result.value
        } else {
          logger.warn("Received create account request where NINO in request body did not match NINO retrieved from auth")
          pagerDutyAlerting.alert("NINOs in create account request do not match")
          Left(ApiAccessError())
        }
    }

  private def createAccount(createAccountRequest: CreateAccountRequest)(
      implicit
      request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = {
    val correlationIdHeader = "requestCorrelationId" -> createAccountRequest.header.requestCorrelationId.toString
    val nino = createAccountRequest.body.nino

    helpToSaveConnector.createAccount(createAccountRequest.body, createAccountRequest.header.requestCorrelationId, createAccountRequest.header.clientCode)
      .map[Either[ApiError, CreateAccountSuccess]] { response ⇒
        response.status match {
          case Status.CREATED ⇒
            logger.info("successfully created account via API", nino, correlationIdHeader)
            Right(CreateAccountSuccess(alreadyHadAccount = false))

          case Status.CONFLICT ⇒
            logger.info("successfully received 409 from create account via API, user already had account", nino, correlationIdHeader)
            Right(CreateAccountSuccess(alreadyHadAccount = true))

          case Status.BAD_REQUEST ⇒
            logger.warn(s"validation of create account request failed: ${request.body}", nino, correlationIdHeader)
            val error = response.parseJson[CreateAccountErrorOldFormat].fold({
              e ⇒
                logger.warn(s"Create account response body was in unexpected format: $e")
                ApiValidationError("request contained invalid or missing details")
            }, { e ⇒
              ApiValidationError(e.errorDetail)
            })
            Left(error)

          case other: Int ⇒
            logger.warn(s"Received unexpected http status in response to create account, status=$other, body=${request.body}", nino, correlationIdHeader)
            pagerDutyAlerting.alert("Received unexpected http status in response to create account")
            Left(ApiBackendError())
        }
      }.recover {
        case e ⇒
          logger.warn(s"Received unexpected error during create account, error=$e", nino, correlationIdHeader)
          pagerDutyAlerting.alert("Failed to make call to createAccount")
          Left(ApiBackendError())
      }
  }

  override def checkEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                                   hc: HeaderCarrier,
                                                                   ec: ExecutionContext): CheckEligibilityResponseType = {
    val timer = metrics.apiEligibilityCallTimer.time()
    val correlationIdHeader = correlationIdHeaderName -> correlationId.toString

    val result = validateCheckEligibilityRequest(nino, correlationIdHeader, timer) {
      _ ⇒
        helpToSaveConnector.checkEligibility(nino, correlationId)
          .map {
            response ⇒
              response.status match {
                case OK ⇒
                  val _ = timer.stop()
                  val result = response.parseJson[EligibilityCheckResponse].flatMap(_.toApiEligibility)
                  result.fold({
                    e ⇒
                      logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e", nino, correlationIdHeader)
                      pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
                  }, _ ⇒
                    logger.info(s"Call to check eligibility successful, received 200 (OK)", nino, correlationIdHeader)
                  )
                  result.leftMap(e ⇒ ApiBackendError())

                case other: Int ⇒
                  metrics.apiEligibilityCallErrorCounter.inc()
                  logger.warn(s"Call to check eligibility returned status: $other, with response body: ${response.body}", nino, correlationIdHeader)
                  pagerDutyAlerting.alert(s"Received unexpected http status in response to eligibility check: $other")
                  Left(ApiBackendError())

              }
          }.recover {
            case e ⇒
              metrics.apiEligibilityCallErrorCounter.inc()
              logger.warn(s"Call to check eligibility failed, error: ${e.getMessage}", nino, correlationIdHeader)
              pagerDutyAlerting.alert("Failed to make call to check eligibility")
              Left(ApiBackendError())
          }
    }

    val _ = timer.stop()
    result
  }

  override def getAccount(nino: String)(implicit r: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): GetAccountResponseType =
    validateGetAccountRequest { () ⇒ getAccount(nino, UUID.randomUUID()) }

  private def getAccount(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                            hc: HeaderCarrier,
                                                            ec: ExecutionContext): GetAccountResponseType = {
    helpToSaveConnector.getAccount(nino, systemId, correlationId)
      .map {
        response ⇒
          response.status match {
            case OK ⇒
              response.parseJson[HtsAccount].bimap(e ⇒ {
                logger.warn(s"htsAccount json from back end failed to parse to HtsAccount, json is: ${response.json}, error is: $e")
                ApiBackendError()
              },
                a ⇒ Some(toAccount(a)))
            case NOT_FOUND ⇒
              logger.warn(s"NS&I have returned a status of NOT FOUND, response body: ${response.body}")
              Right(None)
            case other ⇒
              logger.warn(s"An error occurred when trying to get the account via the connector, status: $other and body: ${response.body}")
              Left(ApiBackendError())
          }
      }.recover {
        case e ⇒
          logger.warn(s"Call to get account via the connector failed, error message: ${e.getMessage}")
          Left(ApiBackendError())
      }
  }

  private def validateCreateAccountRequest(body:    JsValue,
                                           request: Request[_]
  )(f: CreateAccountRequest ⇒ CreateAccountResponseType): CreateAccountResponseType =
    body.validate[CreateAccountRequest] match {
      case JsSuccess(createAccountRequest, _) ⇒
        (httpHeaderValidator.validateHttpHeaders(true)(request), createAccountRequestValidator.validateRequest(createAccountRequest))
          .mapN { case (_, b) ⇒ b }
          .fold(
            { errors ⇒
              val errorString = s"[${errors.toList.mkString("; ")}]"
              logger.warn(s"Error when validating request: $errorString")
              Left(ApiValidationError(errorString))
            },
            f
          )

      case error: JsError ⇒
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse JSON in request body: $errorString")
        Left(ApiValidationError(s"Could not parse JSON in request: $errorString"))

    }

  private def validateCheckEligibilityRequest(nino:                String,
                                              correlationIdHeader: (String, String),
                                              timer:               Timer.Context)(f: String ⇒ CheckEligibilityResponseType)(implicit request: Request[_]): CheckEligibilityResponseType = {
    (httpHeaderValidator.validateHttpHeaders(false), eligibilityRequestValidator.validateNino(nino))
      .mapN { case (_, b) ⇒ b }
      .fold(
        e ⇒ {
          val error = e.toList.mkString(",")
          logger.warn(s"Could not validate headers: [$error]", nino, correlationIdHeader)
          val _ = timer.stop()
          metrics.apiEligibilityCallErrorCounter.inc()
          Left(ApiValidationError(error))
        }, {
          f
        }
      )
  }

  private def validateGetAccountRequest(f: () ⇒ GetAccountResponseType)(implicit request: Request[_]): GetAccountResponseType = {
    httpHeaderValidator.validateHttpHeaders(false).toEither
      .fold[GetAccountResponseType](
        e ⇒ {
          val errors = s"[${e.toList.mkString("; ")}]"
          logger.warn(s"Error when validating get account request, errors: $errors")
          Left(ApiValidationError(errors))
        },
        _ ⇒ f()
      )
  }

  private def toAccount(account: HtsAccount): Account =
    Account(account.accountNumber, account.canPayInThisMonth, account.isClosed)

}

object HelpToSaveApiServiceImpl {

  private[HelpToSaveApiServiceImpl] case class EligibilityCheckResponse(result: String, resultCode: Int, reason: String, reasonCode: Int)

  private[HelpToSaveApiServiceImpl] object EligibilityCheckResponse {
    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]
  }

  // scalastyle:off magic.number
  private implicit class EligibilityCheckResponseOps(val r: EligibilityCheckResponse) extends AnyVal {
    def toApiEligibility(): Either[String, EligibilityResponse] =
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
    // scalastyle:on magic.number

  }

}
