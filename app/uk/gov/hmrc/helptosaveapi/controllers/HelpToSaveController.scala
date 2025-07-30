/*
 * Copyright 2024 HM Revenue & Customs
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

import cats.instances.string.*
import cats.syntax.eq.*
import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.json.Json.*
import play.api.mvc.*
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{credentials as v2Credentials, nino as v2Nino}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.helptosaveapi.auth.Auth
import uk.gov.hmrc.helptosaveapi.models.AccessType.{PrivilegedAccess, UserRestricted}
import uk.gov.hmrc.helptosaveapi.models.*
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountSuccess, RetrievedUserDetails}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.logging.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosaveapi.logging.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveController @Inject() (
  helpToSaveApiService: HelpToSaveApiService,
  override val authConnector: AuthConnector,
  cc: ControllerComponents
)(implicit config: Configuration, t: LogMessageTransformer, ec: ExecutionContext)
    extends BackendController(cc) with Auth with Logging {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  private val userInfoRetrievals: Retrieval[
    Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~ Option[String] ~ ConfidenceLevel
  ] =
    v2.Retrievals.dateOfBirth and
      v2.Retrievals.itmpName and
      v2.Retrievals.itmpDateOfBirth and
      v2.Retrievals.itmpAddress and
      v2.Retrievals.email and
      v2.Retrievals.confidenceLevel

  def apiErrorToResult(e: ApiError): Result = e match {
    case _: ApiAccessError     => Forbidden(Json.toJson(e))
    case _: ApiValidationError => BadRequest(Json.toJson(e))
    case _: ApiBackendError    => InternalServerError(Json.toJson(e))
  }

  def createAccount(): Action[AnyContent] = authorised(v2Credentials) { implicit request => credentials =>
    def handleResult(result: Either[ApiError, CreateAccountSuccess]): Result =
      result match {
        case Left(e: ApiError) =>
          apiErrorToResult(e)
        case Right(CreateAccountSuccess(alreadyHadAccount)) =>
          if (alreadyHadAccount) {
            Conflict
          } else {
            Created
          }
      }

    AccessType.fromCredentials(credentials) match {
      case Right(PrivilegedAccess) =>
        helpToSaveApiService.createAccountPrivileged(request).map(handleResult)

      case Right(UserRestricted) =>
        // we can't do the user retrievals before this point because the user retrievals
        // will definitely fail with a 500 response from auth for privileged access
        authorised(userInfoRetrievals and v2Nino) { _ =>
          { case dob ~ itmpName ~ itmpDob ~ itmpAddress ~ email ~ confidenceLevel ~ authNino =>
            if (confidenceLevel >= ConfidenceLevel.L200) {
              val retrievedDetails = RetrievedUserDetails(
                authNino,
                itmpName.flatMap(_.givenName),
                itmpName.flatMap(_.familyName),
                itmpDob.orElse(dob),
                itmpAddress,
                email
              )
              helpToSaveApiService.createAccountUserRestricted(request, retrievedDetails).map(handleResult)
            } else {
              Future.successful(Unauthorized("Insufficient confidence level"))
            }
          }
        }(ec)(request)

      case Left(e) =>
        logger.warn(s"Received create account request with unsupported credentials provider type: $e")
        unsupportedCredentialsProviderResult
    }
  }

  def checkEligibilityDeriveNino(): Action[AnyContent] = authorised(v2Credentials) { implicit request => credentials =>
    val correlationId = UUID.randomUUID()
    val result: Future[Result] =
      AccessType.fromCredentials(credentials) match {
        case Right(UserRestricted) =>
          authorised(v2Nino) { _ =>
            _.fold[Future[Result]](Forbidden)(getEligibility(_, correlationId))
          }(ec)(request)

        case Right(PrivilegedAccess) =>
          logger.warn(
            s"no nino exists in the api url, but nino from auth exists and providerType is not 'GovernmentGateway', $correlationId"
          )
          toFuture(Forbidden)

        case Left(e) =>
          logger.warn(
            s"Received check eligibility request with unsupported credentials provider type: $e, $correlationId"
          )
          unsupportedCredentialsProviderResult
      }
    result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
  }

  def checkEligibility(urlNino: String): Action[AnyContent] = authorised(v2Credentials) {
    implicit request => credentials =>
      val correlationId = UUID.randomUUID()
      val result: Future[Result] =
        AccessType.fromCredentials(credentials) match {
          case Right(UserRestricted) =>
            authorised(v2Nino) { _ =>
              _.fold[Future[Result]] {
                logger.warn(
                  s"nino exists in the api url and nino not successfully retrieved from auth but providerType is GG, $correlationId"
                )
                Forbidden
              } { retrievedNino =>
                if (retrievedNino === urlNino) {
                  getEligibility(retrievedNino, correlationId)
                } else {
                  logger.warn(
                    "NINO from the api url doesn't match with auth retrieved nino",
                    s"retrieved [$retrievedNino], request [$urlNino]",
                    "CorrelationId" -> correlationId.toString
                  )
                  Forbidden
                }
              }
            }(ec)(request)

          case Right(PrivilegedAccess) =>
            getEligibility(urlNino, correlationId)

          case Left(e) =>
            logger.warn(
              s"Received check eligibility request with unsupported credentials provider type: $e, $correlationId"
            )
            unsupportedCredentialsProviderResult
        }

      result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
  }

  private def getEligibility(
    nino: String,
    correlationId: UUID
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    helpToSaveApiService.checkEligibility(nino, correlationId).map {
      case Right(response) => Ok(toJson(response))
      case Left(e: ApiError) =>
        apiErrorToResult(e)
    }

  def getAccount(): Action[AnyContent] = authorised(v2Nino) { implicit request =>
    _ match {
      case Some(nino) =>
        helpToSaveApiService
          .getAccount(nino)
          .map {
            case Right(Some(account)) => Ok(Json.toJson(account))
            case Right(None)          => NotFound
            case Left(e: ApiError) =>
              apiErrorToResult(e)
          }

      case None =>
        logger.warn("There was no nino retrieved from auth")
        Forbidden
    }
  }

  val unsupportedCredentialsProviderResult: Result =
    Forbidden(
      Json.toJson(
        ApiAccessError("UNSUPPORTED_CREDENTIALS_PROVIDER", "credentials provider not recognised").asInstanceOf[ApiError]
      )
    )
}
