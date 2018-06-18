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

import cats.instances.string._
import cats.syntax.eq._
import cats.instances.future._
import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosaveapi.auth.Auth
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBackendError.CreateAccountBackendErrorOps
import uk.gov.hmrc.helptosaveapi.models.CreateAccountValidationError.CreateAccountValidationErrorOps
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.{WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class HelpToSaveController @Inject() (helpToSaveApiService:       HelpToSaveApiService,
                                      override val authConnector: AuthConnector)(implicit config: Configuration)
  extends Auth(authConnector) with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  def createAccount(): Action[AnyContent] = Action.async { implicit request ⇒

    helpToSaveApiService.createAccount(request).map {
      case Left(a: CreateAccountValidationError) ⇒
        BadRequest(a.toJson())

      case Left(b: CreateAccountBackendError) ⇒
        InternalServerError(b.toJson())

      case Right(_) ⇒ Created
    }
  }

  def checkEligibility(requestNino: Option[String]): Action[AnyContent] = authorised { implicit request ⇒ (authNino, credentials) ⇒
    val correlationId = UUID.randomUUID()

    val result: Future[Result] = (authNino, requestNino) match {
      case (Some(retrievedNino), None) ⇒
        if (credentials.providerType === "GovernmentGateway") {
          getEligibility(retrievedNino, correlationId)
        } else {
          logger.warn("no nino exists in the api url, but nino from auth exists and providerType is not 'GovernmentGateway'")
          toFuture(Forbidden)
        }

      case (Some(retrievedNino), Some(urlNino)) ⇒
        if (retrievedNino === urlNino) {
          getEligibility(retrievedNino, correlationId)
        } else {
          logger.warn("NINO from the api url doesn't match with auth retrieved nino")
          toFuture(Forbidden)
        }

      case (None, None) ⇒
        toFuture(BadRequest)

      case (None, Some(urlNino)) ⇒
        if (credentials.providerType === "PrivilegedApplication") {
          getEligibility(urlNino, correlationId)
        } else {
          logger.warn("nino exists in the api url and nino successfully retrieved from auth but providerType is not 'PrivilegedApplication'")
          toFuture(Forbidden)
        }
    }

    result.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
  }

  private def getEligibility(nino: String, correlationId: UUID)(implicit request: Request[AnyContent],
                                                                hc: HeaderCarrier): Future[Result] = {
    helpToSaveApiService.checkEligibility(nino, correlationId).map {
      case Left(a: ApiErrorValidationError) ⇒
        BadRequest(Json.toJson(a))

      case Left(b: ApiErrorBackendError) ⇒
        InternalServerError(Json.toJson(b))

      case Right(response) ⇒ Ok(toJson(response))
    }
  }

  def getAccount(): Action[AnyContent] = authorised { implicit request ⇒ (authNino, _) ⇒
    authNino match {
      case Some(nino) ⇒ {
        helpToSaveApiService.getAccount(nino)
          .map {
            case Right(account)                   ⇒ Ok(Json.toJson(account))
            case Left(e: ApiErrorBackendError)    ⇒ InternalServerError(Json.toJson(e))
            case Left(e: ApiErrorValidationError) ⇒ BadRequest(Json.toJson(e))
          }
      }
      case None ⇒ {
        logger.warn("There was no nino retrieved from auth")
        Forbidden
      }
    }

  }

}

