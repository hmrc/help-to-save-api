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

package uk.gov.hmrc.helptosaveapi.util

import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport extends TestSupport {

  val nino = "AE123456C"

  val credentials = Credentials("123-id", "GovernmentGateway")

  val retrievals = new ~(Some(nino), credentials)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail()(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[String] ~ Credentials])(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthResultWithSuccess()(result: Option[String] ~ Credentials) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[String] ~ Credentials])(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, Retrievals.nino and Retrievals.credentials, *, *)
      .returning(Future.successful(result))

}

