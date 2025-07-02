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

package uk.gov.hmrc.helptosaveapi.util

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}

import scala.concurrent.Future

trait AuthSupport extends TestSupport {

  val nino = "AE123456C"

  val ggCredentials: Option[Credentials] = Some(Credentials("123-gg", "GovernmentGateway"))

  val privilegedCredentials: Option[Credentials] = Some(Credentials("123-pa", "PrivilegedApplication"))

  val authProviders: AuthProviders = AuthProviders(GovernmentGateway, PrivilegedApplication)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail()(ex: Throwable): Unit =
    when(
      mockAuthConnector
        .authorise(any(), any())(any(), any())
    )
      .thenReturn(Future.failed(ex))

  def mockAuthResultWithSuccess[A](expectedRetrieval: Retrieval[A])(result: A): OngoingStubbing[Future[A]] =
    when(mockAuthConnector.authorise(any(), eqTo(expectedRetrieval))(any(), any()))
      .thenReturn(Future.successful(result))

}

object AuthSupport {

  implicit class TildeOps[A, B](val t: A ~ B) {

    def and[C](c: C): A ~ B ~ C = new ~(t, c)
  }
}
