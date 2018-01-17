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

import cats.data.ValidatedNel
import cats.instances.list._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.{ActionBuilder, Headers, Request, Result}
import uk.gov.hmrc.helptosaveapi.util.{Logging, toFuture}
import uk.gov.hmrc.helptosaveapi.util.Validation._

import scala.concurrent.Future

class APIHttpHeaderValidator extends Logging {

  import uk.gov.hmrc.helptosaveapi.validators.APIHttpHeaderValidator._

  def validateHeader(errorResponse: ErrorDescription ⇒ Result): ActionBuilder[Request] = new ActionBuilder[Request] {

    def invokeBlock[A](request: Request[A],
                       block:   Request[A] ⇒ Future[Result]): Future[Result] =
      validateHttpHeaders(request).fold(
        { e ⇒
          val errorString = s"[${e.toList.mkString(",")}]"
          logger.warn(s"Could not validate headers: $errorString")
          errorResponse(errorString)
        },
        block
      )
  }

  private def validateHttpHeaders[A](request: Request[A]): ValidatedNel[String, Request[A]] = {
    val headers = request.headers

    val contentTypeCheck: ValidatedNel[String, Option[String]] = validationFromBoolean(request.contentType)(
      _.contains(ContentTypes.JSON),
      contentType ⇒ s"content type was not JSON: ${contentType.getOrElse("")}")

    val acceptCheck: ValidatedNel[String, Headers] = validationFromBoolean(headers)(
      _.get(HeaderNames.ACCEPT).exists(_.contains(expectedAcceptType)),
      _ ⇒ s"accept did not contain expected mime type '$expectedAcceptType'")

    val txmHeadersCheck: ValidatedNel[String, List[String]] = {
      val listOfValidations: List[ValidatedNel[String, String]] = expectedTxmHeaders.map(expectedKey ⇒
        validationFromBoolean(expectedKey)(
          headers.get(_).isDefined,
          _ ⇒ s"Could not find header '$expectedKey'"
        )
      )
      listOfValidations.traverse[Validation, String](identity)
    }

    (contentTypeCheck |@| acceptCheck |@| txmHeadersCheck).map{ case _ ⇒ request }
  }

}

object APIHttpHeaderValidator {

  type ErrorDescription = String

  private[helptosaveapi] val expectedTxmHeaders: List[String] = List(
    "Gov-Client-User-ID",
    "Gov-Client-Timezone",
    "Gov-Vendor-Version",
    "Gov-Vendor-Instance-ID"
  )

  private val expectedAcceptType: String = "application/vnd.hmrc.1.0+json"

}
