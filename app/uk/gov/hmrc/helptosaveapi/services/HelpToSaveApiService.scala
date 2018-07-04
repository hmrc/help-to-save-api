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
import com.codahale.metrics.Timer
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request}
import play.mvc.Http.Status
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.controllers.HelpToSaveController.CreateAccountErrorOldFormat
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.models.createaccount._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService.{CheckEligibilityResponseType, CreateAccountResponseType, GetAccountResponseType}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiServiceImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosaveapi.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, PagerDutyAlerting, base64Encode, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator, EmailValidation}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveApiServiceImpl])
trait HelpToSaveApiService {

  def createAccountPrivileged(request: Request[AnyContent])(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType

  def createAccountUserRestricted(request:              Request[AnyContent],
                                  retrievedUserDetails: RetrievedUserDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType

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
                                                                                  logMessageTransformer: LogMessageTransformer,
                                                                                  emailValidation:       EmailValidation)
  extends HelpToSaveApiService with CreateAccountBehaviour with Logging {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountRequestValidator: CreateAccountRequestValidator = new CreateAccountRequestValidator

  val eligibilityRequestValidator: EligibilityRequestValidator = new EligibilityRequestValidator

  val systemId: String = config.underlying.getString("system-id")

  override def createAccountUserRestricted(request:              Request[AnyContent],
                                           retrievedUserDetails: RetrievedUserDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = { //scalastyle:ignore

    val timer = metrics.apiCreateAccountUserRestrictedCallTimer.time()

    val result = request.body.asJson.fold[CreateAccountResponseType] {
      Future.successful(Left(ApiValidationError("NO_JSON", "no JSON found in request body")))
    } { json ⇒
      val missingMandatoryFields = CreateAccountField.missingMandatoryFields(json)

      if (missingMandatoryFields.isEmpty) {
        storeEmailThenCreateAccount(json, retrievedUserDetails.nino, request)
      } else {
        (for {
          updatedJson ← EitherT.fromEither[Future](fillInMissingDetailsGG(json, missingMandatoryFields, retrievedUserDetails))
          r ← EitherT(storeEmailThenCreateAccount(updatedJson, retrievedUserDetails.nino, request))
        } yield r
        ).value
      }
    }

    val _ = timer.stop()
    result.map(_.leftMap { e ⇒
      metrics.apiCreateAccountUserRestrictedCallErrorCounter.inc()
      e
    })
  }

  override def createAccountPrivileged(request: Request[AnyContent])(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType = { //scalastyle:ignore
    val timer = metrics.apiCreateAccountPrivilegedCallTimer.time()

    val result = request.body.asJson.fold[CreateAccountResponseType](
      Future.successful(Left(ApiValidationError("NO_JSON", "no JSON found in request body")))
    ) {
        storeEmailThenCreateAccount(_, None, request)
      }

    val _ = timer.stop()
    result.map(_.leftMap { e ⇒
      metrics.apiCreateAccountPrivilegedCallErrorCounter.inc()
      e
    })
  }

  private def storeEmailThenCreateAccount(json:          JsValue,
                                         retrievedNINO: Option[String],
                                         request:       Request[_])(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType =
    validateCreateAccountRequest(json, request) {

      case CreateAccountRequest(header, body) ⇒

        if (retrievedNINO.forall(_ === body.nino)) {

          storeEmail(body, header.requestCorrelationId).flatMap {

            case Left(_)  ⇒ Left(ApiBackendError())

            case Right(_) ⇒ createAccount(body, header, request)

          }
        } else {
          logger.warn("Received create account request where NINO in request body did not match NINO retrieved from auth")
          pagerDutyAlerting.alert("NINOs in create account request do not match")
          Left(ApiAccessError())
        }
    }

  private def createAccount(body: CreateAccountBody, header: CreateAccountHeader, request: Request[_])(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val correlationIdHeader = "requestCorrelationId" -> header.requestCorrelationId.toString
    logger.info(s"Create Account Request has been made with headers: ${header.show}")

    helpToSaveConnector.createAccount(body, header.requestCorrelationId, header.clientCode).map[Either[ApiError, CreateAccountSuccess]] { response ⇒
      response.status match {
        case Status.CREATED ⇒
          logger.info("successfully created account via API", body.nino, correlationIdHeader)
          Right(CreateAccountSuccess(alreadyHadAccount = false))

        case Status.CONFLICT ⇒
          logger.info("successfully received 409 from create account via API, user already had account", body.nino, correlationIdHeader)
          Right(CreateAccountSuccess(alreadyHadAccount = true))

        case Status.BAD_REQUEST ⇒
          logger.warn(s"validation of create account request failed: ${request.body}", body.nino, correlationIdHeader)
          val error = response.parseJson[CreateAccountErrorOldFormat].fold({
            e ⇒
              logger.warn(s"Create account response body was in unexpected format: $e")
              ApiValidationError("request contained invalid or missing details")
          }, { e ⇒
            ApiValidationError(e.errorDetail)
          })
          Left(error)

        case other: Int ⇒
          logger.warn(s"Received unexpected http status in response to create account, status=$other, body=${request.body}", body.nino, correlationIdHeader)
          pagerDutyAlerting.alert("Received unexpected http status in response to create account")
          Left(ApiBackendError())
      }
    }.recover {
      case e ⇒
        logger.warn(s"Received unexpected error during create account, error=$e", body.nino, correlationIdHeader)
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

  override def getAccount(nino: String)(implicit request: Request[AnyContent],
                                        hc: HeaderCarrier,
                                        ec: ExecutionContext): GetAccountResponseType = {
    validateGetAccountRequest {
      () ⇒
        val correlationId: UUID = UUID.randomUUID()

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
  }

  //store email in mongo if the communicationPreference is set to 02
  private def storeEmail(body: CreateAccountBody, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Unit]] = {
    if (body.contactDetails.communicationPreference === "02") {
      body.contactDetails.email match {
        case Some(email) ⇒
          val correlationIdHeader = "requestCorrelationId" -> correlationId.toString
          helpToSaveConnector.storeEmail(base64Encode(email), correlationId).map[Either[String, Unit]] {
            response ⇒
              response.status match {
                case Status.CREATED ⇒
                  logger.info("successfully stored email for the api user, proceeding with create account", body.nino, correlationIdHeader)
                  Right(())
                case other: Int ⇒
                  logger.warn(s"could not store email in mongo for the api user, not creating account, status: $other", body.nino, correlationIdHeader)
                  pagerDutyAlerting.alert("unexpected status during storing email for the api user")
                  Left("could not store email in mongo for the api user")
              }
          }.recover {
            case e ⇒
              logger.warn(s"error during storing email for the api user, error=${e.getMessage}")
              pagerDutyAlerting.alert("could not store email in mongo for the api user")
              Left("could not store email in mongo for the api user")
          }

        case None ⇒ //should never happen as we already validate the email if communicationPreference is 02
          logger.warn("no email found in the request body but communicationPreference is 02")
          Left("no email found in the request body but communicationPreference is 02")

      }
    } else {
      Right(())
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
