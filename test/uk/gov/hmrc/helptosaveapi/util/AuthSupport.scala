/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.util

import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport extends TestSupport {

  val nino = "AE123456C"

  val ggCredentials = GGCredId("123-gg")

  val privilegedCredentials = PAClientId("123-pa")

  val authProviders: AuthProviders = AuthProviders(GovernmentGateway, PrivilegedApplication)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail()(ex: Throwable): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[String] ~ retrieve.Credentials])(
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(authProviders, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthResultWithSuccess[A](expectedRetrieval: Retrieval[A])(result: A) =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
      .expects(authProviders, expectedRetrieval, *, *)
      .returning(Future.successful(result))

}

object AuthSupport {

  implicit class TildeOps[A, B](val t: A ~ B) {

    def and[C](c: C): A ~ B ~ C = new ~(t, c)

  }

}
