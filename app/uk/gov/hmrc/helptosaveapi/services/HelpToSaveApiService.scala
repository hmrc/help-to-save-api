/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models.EnrolmentStatus.Enrolled
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.models.Account.fromHtsAccount
import uk.gov.hmrc.helptosaveapi.models.createaccount._
import uk.gov.hmrc.helptosaveapi.repo.EligibilityStore
import uk.gov.hmrc.helptosaveapi.repo.EligibilityStore.EligibilityResponseWithNINO
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService.{CheckEligibilityResponseType, CreateAccountResponseType, GetAccountResponseType}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiServiceImpl.{CreateAccountErrorResponse, EligibilityCheckResponse}
import uk.gov.hmrc.helptosaveapi.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveapi.util.JsErrorOps._
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting, toFuture}
import uk.gov.hmrc.helptosaveapi.validators.{APIHttpHeaderValidator, CreateAccountRequestValidator, EligibilityRequestValidator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

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
class HelpToSaveApiServiceImpl @Inject() (val helpToSaveConnector:       HelpToSaveConnector,
                                          metrics:                       Metrics,
                                          val pagerDutyAlerting:         PagerDutyAlerting,
                                          createAccountRequestValidator: CreateAccountRequestValidator,
                                          eligibilityStore:              EligibilityStore)(implicit config: Configuration, logMessageTransformer: LogMessageTransformer)
  extends HelpToSaveApiService with CreateAccountBehaviour with EmailBehaviour with Logging {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val httpHeaderValidator: APIHttpHeaderValidator = new APIHttpHeaderValidator

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
                                          request:       Request[_]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResponseType =
    validateCreateAccountRequest(json, request) {

      case CreateAccountRequest(header, body) ⇒

        if (retrievedNINO.forall(_ === body.nino)) {

          val p: CheckEligibilityResponseType = validateCorrelationId(header.requestCorrelationId, body.nino)
          val q: Future[Either[ApiError, Boolean]] = validateBankDetails(body.nino, body.bankDetails)

          val result: CheckEligibilityResponseType = (p, q).mapN[Either[ApiError, EligibilityResponse]]{
            case (Right(eligibility), Right(true)) ⇒ Right(eligibility)
            case (Right(_), Right(false))          ⇒ Left(ApiValidationError("INVALID_BANK_DETAILS"))
            case (Left(apiError), _)               ⇒ Left(apiError)
            case (_, Left(apiError))               ⇒ Left(apiError)
          }

          result.flatMap {
            case Right(eligibilityResponse) ⇒ eligibilityResponse match {
              case aer: ApiEligibilityResponse ⇒
                if (body.contactDetails.communicationPreference === "02") {
                  (for {
                    email ← EitherT.fromOption(body.contactDetails.email, ApiValidationError("no email found in the request body but communicationPreference is 02"))
                    _ ← EitherT(storeEmail(body.nino, email, header.requestCorrelationId))
                    r ← EitherT(createAccount(body, header, aer))
                  } yield r).value
                } else {
                  (for {
                    r ← EitherT(createAccount(body, header, aer))
                  } yield r).value
                }

              case _: AccountAlreadyExists ⇒ Right(CreateAccountSuccess(alreadyHadAccount = true))
            }

            case Left(apiError) ⇒ Left(apiError)
          }
        } else {
          logger.warn("Received create account request where NINO in request body did not match NINO retrieved from auth",
            s"retrieved [$retrievedNINO], request [${body.nino}]")
          pagerDutyAlerting.alert("NINOs in create account request do not match")
          Left(ApiAccessError())
        }
    }

  private def createAccount(body:                CreateAccountBody,
                            header:              CreateAccountHeader,
                            eligibilityResponse: ApiEligibilityResponse)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    metrics.apiCreateAccountCallCounter(header.clientCode).inc()

    val correlationIdHeader = "requestCorrelationId" -> header.requestCorrelationId.toString
    logger.info(s"Create Account Request has been made with headers: ${header.show}")

    val eligibility = eligibilityResponse.eligibility
    val reasonCode: Int =
      if (eligibility.hasUC && eligibility.hasWTC) {
        8
      } else if (eligibility.hasWTC) {
        7
      } else {
        6
      }

    helpToSaveConnector.createAccount(body, header.requestCorrelationId, header.clientCode, reasonCode).map[Either[ApiError, CreateAccountSuccess]] { response ⇒
      response.status match {
        case Status.CREATED ⇒
          logger.info("successfully created account via API", body.nino, correlationIdHeader)
          Right(CreateAccountSuccess(alreadyHadAccount = false))

        case Status.CONFLICT ⇒
          logger.info("successfully received 409 from create account via API, user already had account", body.nino, correlationIdHeader)
          Right(CreateAccountSuccess(alreadyHadAccount = true))

        case Status.BAD_REQUEST ⇒
          logger.warn("validation of api create account request failed", body.nino, correlationIdHeader)
          val error = response.parseJson[CreateAccountErrorResponse].fold({
            e ⇒
              logger.warn(s"Create account response body was in unexpected format: $e")
              ApiValidationError("request contained invalid or missing details")
          }, { e ⇒
            ApiValidationError(e.errorDetail)
          })

          Left(error)

        case other: Int ⇒
          logger.warn(s"Received unexpected http status in response to create account, status=$other", body.nino, correlationIdHeader)
          pagerDutyAlerting.alert("Received unexpected http status in response to create account")
          Left(ApiBackendError())
      }
    }.recover {
      case NonFatal(e) ⇒
        logger.warn(s"Received unexpected error during create account, error=$e", body.nino, correlationIdHeader)
        pagerDutyAlerting.alert("Failed to make call to createAccount")
        Left(ApiBackendError())
    }
  }

  override def checkEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                                   hc: HeaderCarrier,
                                                                   ec: ExecutionContext): CheckEligibilityResponseType = {

    val result: EitherT[Future, ApiError, EligibilityResponse] =
      for {
        enrolmentStatus ← EitherT.liftF(getUserEnrolmentStatus(nino, correlationId))
        ecResult ← EitherT(performCheckEligibility(nino, correlationId, enrolmentStatus))
      } yield ecResult

    result.value
  }

  private def performCheckEligibility(nino:            String,
                                      correlationId:   UUID,
                                      enrolmentStatus: Option[EnrolmentStatus])(implicit request: Request[AnyContent],
                                                                                hc: HeaderCarrier,
                                                                                ec: ExecutionContext): CheckEligibilityResponseType = {

    enrolmentStatus match {
      case Some(Enrolled(_)) ⇒ Right(AccountAlreadyExists())
      case other ⇒ {
        val timer = metrics.apiEligibilityCallTimer.time()
        val correlationIdHeader = correlationIdHeaderName -> correlationId.toString

        val result = validateCheckEligibilityRequest(nino, correlationIdHeader, timer) {
          _ ⇒
            helpToSaveConnector.checkEligibility(nino, correlationId)
              .map[CheckEligibilityResponseType] {
                ecResponse ⇒
                  ecResponse.status match {
                    case OK ⇒
                      val _ = timer.stop()
                      val result = ecResponse.parseJson[EligibilityCheckResponse].flatMap(_.toApiEligibility)
                      result.fold({
                        e ⇒
                          logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e", nino, correlationIdHeader)
                          pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
                          Left(ApiBackendError())
                      }, res ⇒ {
                        logger.info(s"Call to check eligibility successful, received 200 (OK)", nino, correlationIdHeader)
                        storeEligibilityResultInMongo(res, correlationId, nino, correlationIdHeader)
                      }
                      )

                    case other: Int ⇒
                      metrics.apiEligibilityCallErrorCounter.inc()
                      logger.warn(s"Call to check eligibility returned status: $other", nino, correlationIdHeader)
                      pagerDutyAlerting.alert(s"Received unexpected http status in response to eligibility check: $other")
                      Left(ApiBackendError())

                  }
              }.recover {
                case NonFatal(e) ⇒
                  metrics.apiEligibilityCallErrorCounter.inc()
                  logger.warn(s"Call to check eligibility failed, error: ${e.getMessage}", nino, correlationIdHeader)
                  pagerDutyAlerting.alert("Failed to make call to check eligibility")
                  toFuture(Left(ApiBackendError()))
              }.flatMap(identity)
        }

        val _ = timer.stop()
        result

      }
    }

  }

  private def getUserEnrolmentStatus(nino: String, correlationId: UUID)(implicit ex: ExecutionContext, hc: HeaderCarrier): Future[Option[EnrolmentStatus]] = {
    helpToSaveConnector.getUserEnrolmentStatus(nino, correlationId).map[Option[EnrolmentStatus]] {
      res ⇒
        res.status match {
          case OK ⇒ res.parseJson[EnrolmentStatus].fold({
            e ⇒
              logger.warn(s"Get user enrolment status response body was in unexpected format: $e")
              None
          }, { enrolmentStatus ⇒ Some(enrolmentStatus)
          })
          case other: Int ⇒
            logger.warn(s"Could not get user enrolment status from the back end, status: $other")
            None
        }
    }.recover{
      case NonFatal(e) ⇒
        logger.warn(s"Could not get user enrolment status, future failed: ${e.getMessage}")
        None
    }
  }

  private def storeEligibilityResultInMongo(result:              EligibilityResponse,
                                            correlationId:       UUID,
                                            nino:                String,
                                            correlationIdHeader: (String, String))(implicit ex: ExecutionContext): Future[Either[ApiBackendError, EligibilityResponse]] = {
    eligibilityStore.put(correlationId, result, nino).map[Either[ApiBackendError, EligibilityResponse]] {
      res ⇒
        res.fold[Either[ApiBackendError, EligibilityResponse]](
          error ⇒ {
            logger.warn(s"error storing api eligibility result in mongo, cause=$error", nino, correlationIdHeader)
            Left(ApiBackendError())
          },
          _ ⇒ Right(result)
        )
    }
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
                    a ⇒ Some(fromHtsAccount(a)))
                case NOT_FOUND ⇒
                  logger.warn(s"NS&I have returned a status of NOT FOUND, response body: ${response.body}")
                  Right(None)
                case other ⇒
                  logger.warn(s"An error occurred when trying to get the account via the connector, status: $other")
                  Left(ApiBackendError())
              }
          }.recover {
            case NonFatal(e) ⇒
              logger.warn(s"Call to get account via the connector failed, error message: ${e.getMessage}")
              Left(ApiBackendError())
          }
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

  private def validateCorrelationId(cId: UUID, nino: String)(implicit ec: ExecutionContext): Future[Either[ApiError, EligibilityResponse]] =
    eligibilityStore.get(cId).map[Either[ApiError, EligibilityResponse]] {
      res ⇒
        res.fold(
          error ⇒ {
            logger.warn(s"error during retrieving api- eligibility from mongo, error=$error")
            Left(ApiBackendError())
          }, {
            case None ⇒ Left(ApiValidationError(s"requestCorrelationId($cId) is not valid"))
            case Some(EligibilityResponseWithNINO(eligibility, n)) ⇒
              eligibility match {
                case a: ApiEligibilityResponse ⇒
                  if (nino === n) {
                    if (a.eligibility.isEligible) {
                      Right(a)
                    } else {
                      Left(ApiValidationError(s"invalid api createAccount request, user is not eligible"))
                    }
                  } else {
                    Left(ApiValidationError(s"nino was not compatible with correlation Id"))
                  }

                case b: AccountAlreadyExists ⇒ Right(b)
              }
          }
        )
    }

  private def validateBankDetails(nino:             NINO,
                                  maybeBankDetails: Option[CreateAccountBody.BankDetails])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ApiError, Boolean]] = {

    maybeBankDetails match {
      case Some(bankDetails) ⇒
        val timerContext = metrics.apiValidateBankDetailsTimer.time()
        helpToSaveConnector.validateBankDetails(ValidateBankDetailsRequest(nino, bankDetails.sortCode, bankDetails.accountNumber)).map[Either[ApiError, Boolean]] { response ⇒
          response.status match {
            case Status.OK ⇒
              val _ = timerContext.stop()
              Try((response.json \ "isValid").as[Boolean]) match {
                case Success(isvalid) ⇒ Right(isvalid)
                case Failure(error) ⇒
                  metrics.apiValidateBankDetailsErrorCounter.inc()
                  logger.warn(s"couldn't parse /validate-bank-details response from BE, error=${error.getMessage}. Body was ${response.body}")
                  Left(ApiBackendError())
              }
            case other: Int ⇒
              metrics.apiValidateBankDetailsErrorCounter.inc()
              logger.warn(s"unexpected status($other) from /validate-bank-details endpoint from BE")
              Left(ApiBackendError())
          }
        }

      case None ⇒ Right(true)
    }
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

}

object HelpToSaveApiServiceImpl {

  private[HelpToSaveApiServiceImpl] case class EligibilityCheckResult(result: String, resultCode: Int, reason: String, reasonCode: Int)

  private[HelpToSaveApiServiceImpl] object EligibilityCheckResult {
    implicit val format: Format[EligibilityCheckResult] = Json.format[EligibilityCheckResult]
  }

  private[HelpToSaveApiServiceImpl] case class EligibilityCheckResponse(eligibilityCheckResult: EligibilityCheckResult, threshold: Option[Double])

  private[HelpToSaveApiServiceImpl] object EligibilityCheckResponse {
    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]
  }

  // scalastyle:off magic.number
  private implicit class EligibilityCheckResponseOps(val r: EligibilityCheckResponse) extends AnyVal {
    def toApiEligibility(): Either[String, EligibilityResponse] =
      (r.eligibilityCheckResult.resultCode, r.eligibilityCheckResult.reasonCode) match {
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

  case class CreateAccountErrorResponse(errorMessage: String, errorDetail: String)

  object CreateAccountErrorResponse {
    implicit val format: Format[CreateAccountErrorResponse] = Json.format[CreateAccountErrorResponse]

    implicit class CreateAccountErrorResponseOps(val errorResponse: CreateAccountErrorResponse) extends AnyVal {
      def toJson(): JsValue = format.writes(errorResponse)
    }

  }

}
