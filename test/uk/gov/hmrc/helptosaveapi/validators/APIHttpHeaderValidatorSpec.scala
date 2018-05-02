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

package uk.gov.hmrc.helptosaveapi.validators

import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.Results.{BadRequest, Ok}
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosaveapi.util.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class APIHttpHeaderValidatorSpec extends TestSupport {

  val validator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  val createAccountAction: Action[AnyContent] = validator.validateHeaderForCreateAccount(_ ⇒ play.api.mvc.Results.BadRequest)(_ ⇒ play.api.mvc.Results.Ok)

  val eligibilityCheckAction: Action[AnyContent] = validator.validateHeaderForEligibilityCheck(_ ⇒ play.api.mvc.Results.BadRequest)(_ ⇒ play.api.mvc.Results.Ok)

  def await[A](a: Future[A]): A = Await.result(a, 5.seconds)

  def requestWithHeaders(headers: Map[String, String]): Request[_] =
    FakeRequest.apply("", "", Headers(headers.toList: _*), "")

  "The HeaderValidatorImpl" when {

    "handling CreateAccount requests" must {

        def result(headers: Map[String, String]): Result =
          await(createAccountAction(requestWithHeaders(headers)).run())

      val validRequestHeaders: Map[String, String] = Map(
        APIHttpHeaderValidator.expectedTxmHeaders.map(_ → "value") ++ List(
          HeaderNames.CONTENT_TYPE → ContentTypes.JSON,
          HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json"
        ): _*
      )

      behave like testCommon(validRequestHeaders, result, true)
    }

    "handling eligibility requests" must {

      val validRequestHeaders: Map[String, String] = Map(
        APIHttpHeaderValidator.expectedTxmHeaders.map(_ → "value") ++ List(
          HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json"
        ): _*
      )

        def result(headers: Map[String, String]): Result =
          await(eligibilityCheckAction(requestWithHeaders(headers)).run())

      behave like testCommon(validRequestHeaders, result, false)
    }

      def testCommon(validRequestHeaders: Map[String, String],
                     result:              Map[String, String] ⇒ Result,
                     checkContentType:    Boolean): Unit = {

        "allow requests with valid headers" in {
          result(validRequestHeaders) shouldBe Ok
        }

        "flag as invalid requests" which {

          if (checkContentType) {
            "do not have content type JSON" in {
              result(validRequestHeaders.updated(HeaderNames.CONTENT_TYPE, ContentTypes.HTML)) shouldBe BadRequest
            }
          }

          "do not have 'application/vnd.hmrc.1.0+json' in the accept header" in {
            result(validRequestHeaders.updated(HeaderNames.ACCEPT, "invalid")) shouldBe BadRequest
            result(validRequestHeaders.updated(HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+jsonx")) shouldBe BadRequest
            result(validRequestHeaders - HeaderNames.ACCEPT) shouldBe BadRequest

          }

          "does not have all the expected TxM headers" in {
            (1 to APIHttpHeaderValidator.expectedTxmHeaders.size).foreach { size ⇒
              APIHttpHeaderValidator.expectedTxmHeaders.combinations(size).foreach { headers ⇒
                result(validRequestHeaders -- headers) shouldBe BadRequest
              }
            }
          }
        }
      }
  }

}
