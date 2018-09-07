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

import java.time.LocalDate
import java.util.UUID

import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import org.joda.time.{LocalDate ⇒ JodaLocalDate}
import play.api.Configuration
import play.api.libs.json.Json._
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.{Name ⇒ RetrievedName, _}
import uk.gov.hmrc.helptosaveapi.auth.Auth
import uk.gov.hmrc.helptosaveapi.models.AccessType.{PrivilegedAccess, UserRestricted}
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountSuccess, RetrievedUserDetails}
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class HelpToSaveController @Inject() (helpToSaveApiService:       HelpToSaveApiService,
                                      override val authConnector: AuthConnector)(implicit config: Configuration, t: LogMessageTransformer)
  extends Auth(authConnector) with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val userInfoRetrievals: Retrieval[RetrievedName ~ Option[JodaLocalDate] ~ ItmpName ~ Option[JodaLocalDate] ~ ItmpAddress ~ Option[String]] =
    Retrievals.name and
      Retrievals.dateOfBirth and
      Retrievals.itmpName and
      Retrievals.itmpDateOfBirth and
      Retrievals.itmpAddress and
      Retrievals.email

  def createAccount(): Action[AnyContent] = authorised(Retrievals.authProviderId) { implicit request ⇒ credentials ⇒
    def toJavaDate(jodaDate: JodaLocalDate): LocalDate =
        LocalDate.of(jodaDate.getYear, jodaDate.getMonthOfYear, jodaDate.getDayOfMonth)

      def handleResult(result: Either[ApiError, CreateAccountSuccess]): Result =
        result match {
          case Left(e: ApiAccessError)                        ⇒ Forbidden(Json.toJson(e))
          case Left(a: ApiValidationError)                    ⇒ BadRequest(Json.toJson(a))
          case Left(b: ApiBackendError)                       ⇒ InternalServerError(Json.toJson(b))
          case Right(CreateAccountSuccess(alreadyHadAccount)) ⇒ if (alreadyHadAccount) { Conflict } else { Created }
        }

    AccessType.fromLegacyCredentials(credentials) match {
      case Right(PrivilegedAccess) ⇒
        helpToSaveApiService.createAccountPrivileged(request).map(handleResult)

      case Right(UserRestricted) ⇒
        // we can't do the user retrievals before this point because the user retrievals
        // will definitely fail with a 500 response from auth for privileged access
        authorised(userInfoRetrievals and Retrievals.nino){ _ ⇒
          {
            case ggName ~ dob ~ itmpName ~ itmpDob ~ itmpAddress ~ email ~ authNino ⇒
              val retrievedDetails = RetrievedUserDetails(
                authNino,
                itmpName.givenName.orElse(ggName.name),
                itmpName.familyName.orElse(ggName.lastName),
                itmpDob.orElse(dob).map(toJavaDate),
                itmpAddress,
                email
              )
              helpToSaveApiService.createAccountUserRestricted(request, retrievedDetails).map(handleResult)
          }
        }(request)

      case Left(e) ⇒
        logger.warn(s"Received create account request with unsupported credentials provider type: $e")
        unsupportedCredentialsProviderResult
    }
  }

  def checkEligibilityDeriveNino(): Action[AnyContent] = authorised(Retrievals.authProviderId) { implicit request ⇒ credentials ⇒
    val correlationId = UUID.randomUUID()

    val result: Future[Result] =
      AccessType.fromLegacyCredentials(credentials) match {
        case Right(UserRestricted) ⇒
          authorised(Retrievals.nino){ _ ⇒
            _.fold[Future[Result]](Forbidden)(getEligibility(_, correlationId))
          }(request)

        case Right(PrivilegedAccess) ⇒
          logger.warn(s"no nino exists in the api url, but nino from auth exists and providerType is not 'GovernmentGateway', $correlationId")
          toFuture(Forbidden)

        case Left(e) ⇒
          logger.warn(s"Received check eligibility request with unsupported credentials provider type: $e, $correlationId")
          unsupportedCredentialsProviderResult
      }

    result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))

  }

  def checkEligibility(urlNino: String): Action[AnyContent] = authorised(Retrievals.authProviderId) { implicit request ⇒ credentials ⇒
    val correlationId = UUID.randomUUID()

    val result: Future[Result] =
      AccessType.fromLegacyCredentials(credentials) match {
        case Right(UserRestricted) ⇒
          authorised(Retrievals.nino){ _ ⇒
            _.fold[Future[Result]] {
              logger.warn(s"nino exists in the api url and nino not successfully retrieved from auth but providerType is GG, $correlationId")
              Forbidden
            }{ retrievedNino ⇒
              if (retrievedNino === urlNino) {
                getEligibility(retrievedNino, correlationId)
              } else {
                logger.warn(s"NINO from the api url doesn't match with auth retrieved nino",
                  s"retrieved [$retrievedNino], request [$urlNino]",
                  "CorrelationId" → correlationId.toString)
                Forbidden
              }
            }
          }(request)

        case Right(PrivilegedAccess) ⇒
          getEligibility(urlNino, correlationId)

        case Left(e) ⇒
          logger.warn(s"Received check eligibility request with unsupported credentials provider type: $e, $correlationId")
          unsupportedCredentialsProviderResult
      }

    result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
  }

  private def getEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                                hc: HeaderCarrier): Future[Result] = {
    helpToSaveApiService.checkEligibility(nino, correlationId).map {
      case Left(e: ApiAccessError)     ⇒ Forbidden(Json.toJson(e))

      case Left(a: ApiValidationError) ⇒ BadRequest(Json.toJson(a))

      case Left(b: ApiBackendError)    ⇒ InternalServerError(Json.toJson(b))

      case Right(response)             ⇒ Ok(toJson(response))
    }
  }

  def getAccount(): Action[AnyContent] = authorised(Retrievals.nino) { implicit request ⇒
    _ match {
      case Some(nino) ⇒
        helpToSaveApiService.getAccount(nino)
          .map {
            case Right(Some(account))        ⇒ Ok(Json.toJson(account))
            case Right(None)                 ⇒ NotFound
            case Left(e: ApiAccessError)     ⇒ Forbidden(Json.toJson(e))
            case Left(e: ApiBackendError)    ⇒ InternalServerError(Json.toJson(e))
            case Left(e: ApiValidationError) ⇒ BadRequest(Json.toJson(e))
          }

      case None ⇒
        logger.warn("There was no nino retrieved from auth")
        Forbidden

    }
  }

  val unsupportedCredentialsProviderResult: Result =
    Forbidden(Json.toJson(ApiAccessError("UNSUPPORTED_CREDENTIALS_PROVIDER", "credentials provider not recognised")))
}
