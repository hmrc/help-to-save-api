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

import akka.stream.Materializer
import org.scalamock.handlers.CallHandler6
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Writes
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}

class TestSupport extends WordSpec with Matchers with MockFactory with WithFakeApplication {

  implicit lazy val materialiser: Materializer = fakeApplication.materializer

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val http: WSHttp = mock[WSHttp]

  def mockPost[A](expectedUrl:  String,
                  expectedBody: A,
                  headers:      Map[String, String])(response: HttpResponse): CallHandler6[String, A, Map[String, String], Writes[A], HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (http.post[A](_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(expectedUrl, expectedBody, headers, *, *, *)
      .returning(response)

}
