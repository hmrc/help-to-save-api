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

import java.util.regex.Matcher

import cats.data.ValidatedNel
import cats.instances.int._
import cats.instances.string._
import cats.syntax.cartesian._
import cats.syntax.eq._
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.util.Validation.validationFromBoolean

class CreateAccountRequestValidator {

  import uk.gov.hmrc.helptosaveapi.validators.CreateAccountRequestValidator._

  def validateRequest(request: CreateAccountRequest): ValidatedNel[String, CreateAccountRequest] = {
    (request.header.validate() |@| request.body.validate()).map(CreateAccountRequest(_, _))

  }
}

object CreateAccountRequestValidator {

  private implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

  implicit class CreateAccountBodyOps(val body: CreateAccountBody) extends AnyVal {

    // checks the communication preference and registration channel - the rest of the body is validated downstream
    def validate(): ValidatedNel[String, CreateAccountBody] = {
      val communicationPreferenceCheck =
        validationFromBoolean(body.contactDetails.communicationPreference)(_ === "00", c ⇒ s"Unknown communication preference: $c")

      val registrationChannelCheck = validationFromBoolean(body.registrationChannel)(_ === "callCentre", r ⇒ s"Unknown registration channel: $r")

      val countryCodeCheck = validationFromBoolean(body.contactDetails.countryCode)(
        _.forall(cc ⇒ cc.removeAllSpaces.cleanupSpecialCharacters.length === 2), error ⇒ s"length of countryCode should be 2: $error"
      )

      (communicationPreferenceCheck |@| registrationChannelCheck |@| countryCodeCheck).map { case _ ⇒ body }
    }
  }

  private val versionRegex: String ⇒ Matcher = "^(\\d\\.)+\\d+$".r.pattern.matcher _

  private val clientCodeRegex: String ⇒ Matcher = "^[A-Z0-9][A-Z0-9_-]+[A-Z0-9]$".r.pattern.matcher _

  implicit class CreateAccountHeaderOps(val header: CreateAccountHeader) extends AnyVal {
    def validate(): ValidatedNel[String, CreateAccountHeader] = {
      val versionCheck = validationFromBoolean(header.version)(versionRegex(_).matches(), v ⇒ s"version has incorrect format: $v")
      val versionLengthCheck =
        validationFromBoolean(header.version)(_.length <= 10, v ⇒ s"max length for version should be 10: $v")

      val clientCodeCheck = validationFromBoolean(header.clientCode)(clientCodeRegex(_).matches(), c ⇒ s"unknown client code $c")
      val clientCodeLengthCheck =
        validationFromBoolean(header.clientCode)(_.length <= 20, v ⇒ s"max length for clientCode should be 20: $v")

      (versionCheck |@| versionLengthCheck |@| clientCodeCheck |@| clientCodeLengthCheck).map { case _ ⇒ header }
    }
  }

}

