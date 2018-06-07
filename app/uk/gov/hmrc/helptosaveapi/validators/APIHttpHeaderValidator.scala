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
import cats.syntax.apply._
import cats.syntax.traverse._
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.{Headers, Request}
import uk.gov.hmrc.helptosaveapi.util.{Logging, ValidatedOrErrorString}
import uk.gov.hmrc.helptosaveapi.util.Validation._

class APIHttpHeaderValidator extends Logging {

  import uk.gov.hmrc.helptosaveapi.validators.APIHttpHeaderValidator._

  def contentTypeCheck(implicit request: Request[_]): ValidatedOrErrorString[Option[String]] = validationFromBoolean(request.contentType)(
    _.contains(ContentTypes.JSON),
    contentType ⇒ s"content type was not JSON: ${contentType.getOrElse("")}")

  def acceptCheck(implicit request: Request[_]): ValidatedOrErrorString[Headers] = validationFromBoolean(request.headers)(
    _.get(HeaderNames.ACCEPT).exists(expectedAcceptTypes.contains(_)),
    _ ⇒ s"accept did not contain one of expected mime types: '${expectedAcceptTypes.mkString(", ")}'")

  def txmHeadersCheck(implicit request: Request[_]): ValidatedOrErrorString[List[String]] = {
    val listOfValidations: List[ValidatedNel[String, String]] = expectedTxmHeaders.map(expectedKey ⇒
      validationFromBoolean(expectedKey)(
        request.headers.get(_).isDefined,
        _ ⇒ s"Could not find header '$expectedKey'"
      )
    )
    listOfValidations.traverse[Validation, String](identity)
  }

  def validateHttpHeaders[A](contentTypeCk: Boolean)(implicit request: Request[A]): ValidatedOrErrorString[Request[A]] =
    if (contentTypeCk) {
      (contentTypeCheck, acceptCheck, txmHeadersCheck).mapN { case _ ⇒ request }
    } else {
      (acceptCheck, txmHeadersCheck).mapN { case _ ⇒ request }
    }

}

object APIHttpHeaderValidator {

  private[helptosaveapi] val expectedTxmHeaders: List[String] = List(
    "Gov-Client-User-ID",
    "Gov-Client-Timezone",
    // "Gov-Client-Public-IP", // these header's are to be expected in a later implementation
    // "Gov-Client-Public-Port",
    // "Gov-Client-Device-ID",
    // "Gov-Client-Local-IP",
    // "Gov-Client-User-Agent",
    // "Gov-Vendor-Public-IP",
    "Gov-Vendor-Version",
    "Gov-Vendor-Instance-ID"
  )

  private val expectedAcceptTypes: List[String] = List("application/vnd.hmrc.1.0+json", "application/vnd.hmrc.2.0+json")

}
