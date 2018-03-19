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

import cats.instances.int._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.string._
import cats.instances.char._
import cats.syntax.cartesian._
import cats.syntax.eq._
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.util.Validation.validationFromBoolean

import scala.annotation.tailrec

class CreateAccountRequestValidator {

  import uk.gov.hmrc.helptosaveapi.validators.CreateAccountRequestValidator._

  def validateRequest(request: CreateAccountRequest): ValidatedNel[String, CreateAccountRequest] = {
    (request.header.validate() |@| request.body.validate()).map(CreateAccountRequest(_, _))
  }
}

object CreateAccountRequestValidator {

  implicit class CreateAccountBodyOps(val body: CreateAccountBody) extends AnyVal {

    // checks the communication preference and registration channel - the rest of the body is validated downstream
    def validate(): ValidatedNel[String, CreateAccountBody] = {

      val forenameCheck = forenameValidation(body.forename)

      val surnameCheck = surnameValidation(body.surname)

      val communicationPreferenceCheck =
        validationFromBoolean(body.contactDetails.communicationPreference)(_ === "00", c ⇒ s"Unknown communication preference: $c")

      val registrationChannelCheck = validationFromBoolean(body.registrationChannel)(_ === "callCentre", r ⇒ s"Unknown registration channel: $r")

      (forenameCheck |@| surnameCheck |@|
        communicationPreferenceCheck |@| registrationChannelCheck |@|
        phoneNumberValidation(body.contactDetails.phoneNumber)).map { case _ ⇒ body }
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

  private def forenameValidation(name: String): ValidatedNel[String, String] =
    (commonNameChecks(name, "forename") |@| forenameNoApostrophe(name)).map { case _ ⇒ name }

  private def surnameValidation(name: String): ValidatedNel[String, String] = {
    val lastCharacterNonSpecial = validatedFromBoolean(name)(!_.lastOption.exists(isSpecial(_)), "surname ended with special character")
    (commonNameChecks(name, "surname") |@| lastCharacterNonSpecial).map { case _ ⇒ name }
  }

  private def phoneNumberValidation(phoneNumber: Option[String]): ValidatedNel[String, Option[String]] = {
    val hasDigit =
      validationFromBoolean(phoneNumber)(
        _.forall(_.exists(_.isDigit)),
        _ ⇒ "phone number did not contain any digits"
      )

    val specialCharacterCheck =
      validationFromBoolean(phoneNumber)(
        _.forall(specialCharacters(_, allowedPhoneNumberSpecialCharacters).isEmpty),
        _ ⇒ "phone number contained invalid characters")

    val letterCheck =
      validationFromBoolean(phoneNumber)(
        _.forall(!_.exists(_.isLetter)),
        _ ⇒ "phone number contained letters")

    (hasDigit |@| specialCharacterCheck |@| letterCheck).map{ case _ ⇒ phoneNumber }
  }

  private[validators] val allowedNameSpecialCharacters = List('-', '&', '.', ',', ''')

  private[validators] val allowedPhoneNumberSpecialCharacters = List('(', ')', '-', '.', '+', ' ')

  private def commonNameChecks(name: String, nameType: String): ValidatedNel[String, String] = {

    val forbiddenSpecialCharacters = specialCharacters(name, allowedNameSpecialCharacters)
    val firstCharacterNonSpecial = validatedFromBoolean(name)(!_.headOption.forall(c ⇒ isSpecial(c)), s"$nameType started with special character")
    val consecutiveSpecialCharacters = validatedFromBoolean(name)(!containsNConsecutiveSpecialCharacters(_, 2),
      s"$nameType contained consecutive special characters")
    val specialCharacterCheck = validatedFromBoolean(forbiddenSpecialCharacters)(_.isEmpty,
      s"$nameType contained invalid special characters")
    val noDigits = validatedFromBoolean(name)(!_.exists(c ⇒ c.isDigit), s"$nameType contained a digit")

    (firstCharacterNonSpecial |@| consecutiveSpecialCharacters |@| specialCharacterCheck |@| noDigits).map { case _ ⇒ name }
  }

  private def forenameNoApostrophe(name: String): ValidatedNel[String, String] = {
    validatedFromBoolean(name)(!_.contains('''), "forename contains an apostrophe")
  }

  /**
   * Return a list of distinct special characters contained in the given string. Special
   * characters found which are contained in `ignore` are not returned
   */
  private def specialCharacters(s: String, ignore: List[Char] = List.empty[Char]): List[Char] =
    s.replaceAllLiterally(" ", "").filter(isSpecial(_, ignore)).toList.distinct

  /** Does the given string contain `n` or more consecutive special characters? */
  private def containsNConsecutiveSpecialCharacters(s: String, n: Int): Boolean =
    containsNConsecutive(s, n, isSpecial(_))

  /**
   * Does the given string contains `n` consecutive characters which satisfy the given predicate?
   * `n` should be one or greater.
   */
  private def containsNConsecutive(s:         String,
                                   n:         Int,
                                   predicate: Char ⇒ Boolean): Boolean = {
      @tailrec
      def loop(s:        List[Char],
               previous: Char,
               count:    Int): Boolean = s match {
        case Nil ⇒
          false
        case head :: tail ⇒
          if (predicate(head) && predicate(previous)) {
            val newCount = count + 1
            if (newCount + 1 === n) {
              true
            } else {
              loop(tail, head, newCount)
            }
          } else {
            loop(tail, head, 0)
          }
      }
    if (n === 1) {
      s.exists(predicate)
    } else if (n > 1) {
      s.toList match {
        // only loop over strings that have length two or greater
        case head :: tail ⇒ loop(tail, head, 0)
        case _            ⇒ false
      }
    } else {
      false
    }
  }

  private def validatedFromBoolean[A](a: A)(isValid: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
    if (isValid(a)) Validated.Valid(a) else Validated.Invalid(NonEmptyList.of(ifFalse))

  /**
   * The given [[Char]] is special if the following is true:
   * - it is not a whitespace character
   * - it is not alphanumeric
   * - it is not contained in `ignore`
   */
  private def isSpecial(c: Char, ignore: List[Char] = List.empty[Char]): Boolean =
    !(c === ' ' || c.isLetterOrDigit || ignore.contains(c))

}

