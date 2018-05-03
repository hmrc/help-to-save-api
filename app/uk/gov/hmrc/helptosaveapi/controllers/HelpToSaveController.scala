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

import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.Json._
import play.api.mvc._
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBadRequestError.CreateAccountBadRequestErrorOps
import uk.gov.hmrc.helptosaveapi.models.CreateAccountInternalServerError.CreateAccountInternalServerErrorOps
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.services.HelpToSaveApiService
import uk.gov.hmrc.helptosaveapi.util.WithMdcExecutionContext
import uk.gov.hmrc.play.bootstrap.controller.BaseController

class HelpToSaveController @Inject() (helpToSaveApiService: HelpToSaveApiService)(implicit config: Configuration)
  extends BaseController with WithMdcExecutionContext {

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  def createAccount(): Action[AnyContent] = Action.async { implicit request ⇒

    helpToSaveApiService.createAccount(request).map {
      case Left(a: CreateAccountBadRequestError) ⇒
        BadRequest(a.toJson())

      case Left(b: CreateAccountInternalServerError) ⇒
        InternalServerError(b.toJson())

      case Right(_) ⇒ Created
    }
  }

  def checkEligibility(nino: String): Action[AnyContent] = Action.async { implicit request ⇒
    val correlationId = UUID.randomUUID()
    helpToSaveApiService.checkEligibility(nino, correlationId).map {
      case Left(a: EligibilityCheckBadRequestError) ⇒
        BadRequest(a.toJson())

      case Left(b: EligibilityCheckInternalServerError) ⇒
        InternalServerError(b.toJson())

      case Right(response) ⇒ Ok(toJson(response))
    }.map(_.withHeaders(correlationIdHeaderName -> correlationId.toString))
  }
}

