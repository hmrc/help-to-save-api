/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalactic.Prettifier.default
import play.api.http.Status
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{credentials, nino as v2Nino}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.helptosaveapi.util.{AuthSupport, Logging, TestSupport}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class AuthSpec extends AuthSupport {

  class TestAuth extends BackendController(mockCc) with Auth with AuthorisedFunctions with Logging {
    override def authConnector: AuthConnector = mockAuthConnector
  }

  val auth = new TestAuth

  val retrieve: Retrieval[Option[String] ~ Option[Credentials]] = v2Nino and credentials

  private def callAuth = auth.authorised(retrieve) { _ =>
    { case nino ~ credentials =>
      Future.successful(Ok("authSuccess"))
    }
  }

  "HelpToSaveAuth" should {

    "return after successful authentication" in {
      val ggCredentials = Credentials("id", "GovernmentGateway")
      val ggRetrievals: Option[String] ~ Option[Credentials] = new ~(Some(nino), Some(ggCredentials))
      mockAuthResultWithSuccess(retrieve)(ggRetrievals)

      val result = Await.result(callAuth(FakeRequest()), 5.seconds)
      status(result) shouldBe Status.OK
    }

    "handle various auth related exceptions and throw an error" in {
      def mockAuthWith(error: String) = mockAuthResultWithFail()(fromString(error))

      val exceptions = List(
        "InsufficientConfidenceLevel" -> Status.FORBIDDEN,
        "InsufficientEnrolments"      -> Status.FORBIDDEN,
        "UnsupportedAffinityGroup"    -> Status.FORBIDDEN,
        "UnsupportedCredentialRole"   -> Status.FORBIDDEN,
        "UnsupportedAuthProvider"     -> Status.FORBIDDEN,
        "BearerTokenExpired"          -> Status.UNAUTHORIZED,
        "MissingBearerToken"          -> Status.UNAUTHORIZED,
        "InvalidBearerToken"          -> Status.UNAUTHORIZED,
        "SessionRecordNotFound"       -> Status.UNAUTHORIZED,
        "IncorrectCredentialStrength" -> Status.FORBIDDEN,
        "unknown-blah"                -> Status.INTERNAL_SERVER_ERROR
      )

      exceptions.foreach { case (error, expectedStatus) =>
        mockAuthWith(error)
        val result = callAuth(FakeRequest())
        status(result) shouldBe expectedStatus
      }
    }

    "handle InternalError exception" in {
      mockAuthResultWithFail()(InternalError("Internal error"))
      val result = callAuth(FakeRequest())
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }
}
