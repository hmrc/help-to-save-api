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

package uk.gov.hmrc.helptosaveapi.validators

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class APIHttpHeaderValidatorSpec extends TestSupport {

  val validator: APIHttpHeaderValidator = new APIHttpHeaderValidator

  def requestWithHeaders(headers: Map[String, String]): Request[?] =
    FakeRequest.apply("", "", Headers(headers.toList*), "")

  "The HeaderValidatorImpl" when {

    "handling CreateAccount requests" must {

      def result(headers: Map[String, String]): ValidatedNel[String, Request[Any]] =
        validator.validateHttpHeaders(true)(using requestWithHeaders(headers))

      val validRequestHeaders: Map[String, String] = Map(
        APIHttpHeaderValidator.expectedTxmHeaders.map(_ -> "value") ++ List(
          HeaderNames.CONTENT_TYPE -> ContentTypes.JSON,
          HeaderNames.ACCEPT       -> "application/vnd.hmrc.2.0+json"
        )*
      )

      behave like testCommon(validRequestHeaders, result, true)
    }

    "handling eligibility requests" must {

      val validRequestHeaders: Map[String, String] = Map(
        APIHttpHeaderValidator.expectedTxmHeaders.map(_ -> "value") ++ List(
          HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
        )*
      )

      def result(headers: Map[String, String]): ValidatedNel[String, Request[Any]] =
        validator.validateHttpHeaders(false)(using requestWithHeaders(headers))

      behave like testCommon(validRequestHeaders, result, false)
    }

    def testCommon(
      headers: Map[String, String],
      result: Map[String, String] => ValidatedNel[String, Request[Any]],
      checkContentType: Boolean
    ): Unit = {

      "allow requests with valid headers" in {
        result(headers).toString shouldBe Valid(requestWithHeaders(headers)).toString
      }

      "flag as invalid requests" which {

        if (checkContentType) {
          "do not have content type JSON" in {
            result(headers.updated(HeaderNames.CONTENT_TYPE, ContentTypes.HTML)) shouldBe
              Invalid(NonEmptyList[String]("content type was not JSON: text/html", Nil))
          }
        }

        "do not have expected accept header" in {

          val errorString = "accept header should match: 'application/vnd.hmrc.2.0+json'"
          result(headers.updated(HeaderNames.ACCEPT, "invalid")) shouldBe
            Invalid(NonEmptyList[String](errorString, Nil))

          result(headers.updated(HeaderNames.ACCEPT, "application/vnd.hmrc.2.0+jsonx")) shouldBe
            Invalid(NonEmptyList[String](errorString, Nil))

          result(headers - HeaderNames.ACCEPT) shouldBe
            Invalid(NonEmptyList[String](errorString, Nil))
        }

        "does not have all the expected TxM headers" in {
          (1 to APIHttpHeaderValidator.expectedTxmHeaders.size).foreach { size =>
            APIHttpHeaderValidator.expectedTxmHeaders.combinations(size).foreach { h =>
              result(headers -- h).isInvalid shouldBe true
            }
          }
        }
      }
    }
  }

}
