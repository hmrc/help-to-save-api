/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.helptosaveapi.util.Logging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Auth extends AuthorisedFunctions { this: BackendController with Logging ⇒

  val authProviders: AuthProviders = AuthProviders(GovernmentGateway, PrivilegedApplication)

  type HtsAction[A] = Request[AnyContent] ⇒ A ⇒ Future[Result]

  def authorised[A](retrievals: Retrieval[A])(action: HtsAction[A]): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(authProviders)
        .retrieve(retrievals) { action(request) }
        .recover { handleFailure() }
    }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case e: NoActiveSession ⇒
      logger.warn(s"no active session was found for api user, reason: ${e.reason}")
      Unauthorized

    case e: InternalError ⇒
      logger.warn(s"Could not authenticate user due to internal error: ${e.reason}")
      InternalServerError

    case ex: AuthorisationException ⇒
      logger.warn(s"could not authenticate api user, error: ${ex.reason}")
      Forbidden
  }
}
