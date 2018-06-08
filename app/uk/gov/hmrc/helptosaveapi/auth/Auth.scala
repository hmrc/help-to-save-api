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

package uk.gov.hmrc.helptosaveapi.auth

import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrievals, ~}
import uk.gov.hmrc.helptosaveapi.util.{Logging, WithMdcExecutionContext}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class Auth(val authConnector: AuthConnector) extends BaseController with AuthorisedFunctions with Logging with WithMdcExecutionContext {

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  type HtsAction = Request[AnyContent] ⇒ (Option[String], Credentials) ⇒ Future[Result]

  def authorised(action: HtsAction): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised() //TODO: whats about CL200 requirement ?
        .retrieve(Retrievals.nino and Retrievals.credentials) {
          case mayBeNino ~ creds ⇒ action(request)(mayBeNino, creds)
        }.recover {
          handleFailure()
        }
    }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      logger.warn("no logged in session was found for api user")
      Unauthorized

    case ex: AuthorisationException ⇒
      logger.warn(s"could not authenticate api user, error: ${ex.reason}")
      Forbidden
  }
}
