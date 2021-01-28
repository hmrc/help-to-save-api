/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.models.createaccount

import play.api.libs.json._

case class CreateAccountRequest(header: CreateAccountHeader, body: CreateAccountBody)

object CreateAccountRequest {

  implicit val writes: Writes[CreateAccountRequest] = Json.writes[CreateAccountRequest]

  implicit val reads: Reads[CreateAccountRequest] = Reads[CreateAccountRequest] { jsValue ⇒
    for {
      header ← (jsValue \ "header").validate[CreateAccountHeader]
      body ← (jsValue \ "body").validate[CreateAccountBody](CreateAccountBody.reads(header.clientCode))
    } yield CreateAccountRequest(header, body)
  }

  private def toOption(j: JsLookupResult): Option[JsValue] = j.toOption

  implicit class CreateAccountJSONOps(val j: JsValue) extends AnyVal {

    def forename(): Option[JsValue] = toOption(j \ "body" \ "forename")

    def surname(): Option[JsValue] = toOption(j \ "body" \ "surname")

    def dateOfBirth(): Option[JsValue] = toOption(j \ "body" \ "dateOfBirth")

    def nino(): Option[JsValue] = toOption(j \ "body" \ "nino")

    def address1(): Option[JsValue] = toOption(j \ "body" \ "contactDetails" \ "address1")

    def address2(): Option[JsValue] = toOption(j \ "body" \ "contactDetails" \ "address2")

    def postcode(): Option[JsValue] = toOption(j \ "body" \ "contactDetails" \ "postcode")

    def email(): Option[JsValue] = toOption(j \ "body" \ "contactDetails" \ "email")

    def registrationChannel(): Option[JsValue] = toOption(j \ "body" \ "registrationChannel")

    def communicationPreference(): Option[JsValue] = toOption(j \ "body" \ "contactDetails" \ "communicationPreference")

  }

}
