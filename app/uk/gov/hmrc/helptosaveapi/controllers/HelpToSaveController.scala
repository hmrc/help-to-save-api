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
import play.api.http.HeaderNames
import play.api.libs.json.{Format, JsValue, Json}
import play.api.libs.json.Json._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.{Name ⇒ RetrievedName}
import uk.gov.hmrc.helptosaveapi.auth.Auth
import uk.gov.hmrc.helptosaveapi.controllers.HelpToSaveController.CreateAccountErrorOldFormat
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.models.createaccount.RetrievedUserDetails
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.{WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.helptosaveapi.util.Credentials._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class HelpToSaveController @Inject() (helpToSaveApiService:       HelpToSaveApiService,
                                      override val authConnector: AuthConnector)(implicit config: Configuration)
  extends Auth(authConnector) with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  val userInfoRetrievals: Retrieval[RetrievedName ~ Option[JodaLocalDate] ~ ItmpName ~ Option[JodaLocalDate] ~ ItmpAddress ~ Option[String]] =
    Retrievals.name and
      Retrievals.dateOfBirth and
      Retrievals.itmpName and
      Retrievals.itmpDateOfBirth and
      Retrievals.itmpAddress and
      Retrievals.email

  def createAccount(): Action[AnyContent] = authorised(userInfoRetrievals and Retrievals.nino and Retrievals.credentials) { implicit request ⇒
    {
      case ggName ~ dob ~ itmpName ~ itmpDob ~ itmpAddress ~ email ~ authNino ~ credentials ⇒
        def toJavaDate(jodaDate: JodaLocalDate): LocalDate =
            LocalDate.of(jodaDate.getYear, jodaDate.getMonthOfYear, jodaDate.getDayOfMonth)

          def toJson(error: ApiError)(implicit request: Request[_]): JsValue = {
            if (request.headers.get(HeaderNames.ACCEPT).exists(_.contains("1.0"))) {
              // use old format for errors if API call is for is v1.0
              Json.toJson(CreateAccountErrorOldFormat(error))
            } else {
              Json.toJson(error)
            }
          }

        val retrievedDetails = RetrievedUserDetails(
          authNino,
          itmpName.givenName.orElse(ggName.name),
          itmpName.familyName.orElse(ggName.lastName),
          itmpDob.orElse(dob).map(toJavaDate),
          itmpAddress,
          email
        )

        helpToSaveApiService.createAccount(request, credentials, retrievedDetails).map {
          case Left(e: ApiAccessError)     ⇒ Forbidden(toJson(e))
          case Left(a: ApiValidationError) ⇒ BadRequest(toJson(a))
          case Left(b: ApiBackendError)    ⇒ InternalServerError(toJson(b))
          case Right(_)                    ⇒ Created
        }
    }
  }

  def checkEligibilityDeriveNino(): Action[AnyContent] = authorised(Retrievals.nino and Retrievals.credentials) { implicit request ⇒
    {
      case authNino ~ credentials ⇒
        val correlationId = UUID.randomUUID()

        val result: Future[Result] = authNino.fold[Future[Result]] {
          if (credentials.isGovernmentGateway()) {
            toFuture(Forbidden)
          } else {
            toFuture(BadRequest)
          }
        } { retrievedNino ⇒
          if (credentials.isGovernmentGateway()) {
            getEligibility(retrievedNino, correlationId)
          } else {
            logger.warn("no nino exists in the api url, but nino from auth exists and providerType is not 'GovernmentGateway'")
            toFuture(Forbidden)
          }
        }
        result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
    }
  }

  def checkEligibility(urlNino: String): Action[AnyContent] = authorised(Retrievals.nino and Retrievals.credentials) { implicit request ⇒
    {
      case authNino ~ credentials ⇒
        val correlationId = UUID.randomUUID()

        val result: Future[Result] = authNino.fold[Future[Result]] {
          if (credentials.isPrivilegedApplication()) {
            getEligibility(urlNino, correlationId)
          } else {
            logger.warn("nino exists in the api url and nino not successfully retrieved from auth but providerType is not 'PrivilegedApplication'")
            toFuture(Forbidden)
          }
        } { retrievedNino ⇒
          if (retrievedNino === urlNino) {
            getEligibility(retrievedNino, correlationId)
          } else {
            logger.warn("NINO from the api url doesn't match with auth retrieved nino")
            toFuture(Forbidden)
          }
        }
        result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
    }
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

}

object HelpToSaveController {

  private[controllers] case class CreateAccountErrorOldFormat(errorMessageId: String, errorMessage: String, errorDetails: String)

  private[controllers] object CreateAccountErrorOldFormat {

    def apply(apiError: ApiError): CreateAccountErrorOldFormat = CreateAccountErrorOldFormat(apiError.code, "error", apiError.message)

    implicit val format: Format[CreateAccountErrorOldFormat] = Json.format[CreateAccountErrorOldFormat]

  }

}
