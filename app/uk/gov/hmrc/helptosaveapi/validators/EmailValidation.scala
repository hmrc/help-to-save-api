/*
 * Copyright 2020 HM Revenue & Customs
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
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.char._
import cats.syntax.apply._
import cats.syntax.eq._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration

import scala.annotation.tailrec

@Singleton
class EmailValidation @Inject() (configuration: Configuration) {

  private val emailMaxTotalLength: Int = configuration.underlying.getInt("email-validation.max-total-length")

  private val emailMaxLocalLength: Int = configuration.underlying.getInt("email-validation.max-local-length")

  private val emailMaxDomainLength: Int = configuration.underlying.getInt("email-validation.max-domain-length")

  private val invalidEmailError = "invalid email"

  private def invalid[A](message: String): ValidatedNel[String, A] = Invalid(NonEmptyList[String](message, Nil))

  private def validatedFromBoolean[A](a: A)(predicate: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
    if (predicate(a)) Valid(a) else invalid(ifFalse)

  private def charactersBeforeAndAfterChar(c: Char)(s: String): Option[(Int, Int)] = {
    @tailrec
    def loop(chars: List[Char], count: Int): Option[(Int, Int)] = chars match {
      case Nil ⇒ None
      case h :: t ⇒
        if (h === c) {
          Some(count → t.length)
        } else {
          loop(t, count + 1)
        }
    }

    loop(s.toList, 0)
  }

  private type ValidOrErrorStrings[A] = ValidatedNel[String, A]

  def validate(email: String): Validated[NonEmptyList[String], String] = {

    val trimmed = email.trim
    val localAndDomainLength = charactersBeforeAndAfterChar('@')(trimmed)
    val domainPart = trimmed.substring(trimmed.lastIndexOf('@') + 1)

    val notBlankCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, "")
    val totalLengthCheck: ValidOrErrorStrings[String] =
      validatedFromBoolean(trimmed)(_.length <= emailMaxTotalLength, invalidEmailError)
    val hasAtSymbolCheck: ValidOrErrorStrings[String] =
      validatedFromBoolean(trimmed)(_.contains('@'), invalidEmailError)

    val hasDotSymbolInDomainCheck: ValidOrErrorStrings[String] =
      validatedFromBoolean(domainPart)(_.contains('.'), invalidEmailError)

    val hasTextAfterAtSymbolButBeforeDotCheck: ValidOrErrorStrings[String] = validatedFromBoolean(domainPart)(
      { text ⇒
        text.contains('.') && text.substring(0, text.indexOf('.')).length > 0
      },
      invalidEmailError
    )

    val hasTextAfterDotCheck: ValidOrErrorStrings[String] = validatedFromBoolean(domainPart)(
      { text ⇒
        text.contains('.') && text.substring(text.lastIndexOf('.') + 1).length > 0
      },
      invalidEmailError
    )

    val localLengthCheck: ValidOrErrorStrings[Option[(Int, Int)]] =
      validatedFromBoolean(localAndDomainLength)(_.forall(_._1 <= emailMaxLocalLength), invalidEmailError)

    val domainLengthCheck: ValidOrErrorStrings[Option[(Int, Int)]] =
      validatedFromBoolean(localAndDomainLength)(_.forall(_._2 <= emailMaxDomainLength), invalidEmailError)

    val localBlankCheck: ValidOrErrorStrings[Option[(Int, Int)]] =
      validatedFromBoolean(localAndDomainLength)(_.forall(_._1 > 0), invalidEmailError)

    val domainBlankCheck: ValidOrErrorStrings[Option[(Int, Int)]] =
      validatedFromBoolean(localAndDomainLength)(_.forall(_._2 > 0), invalidEmailError)

    (
      notBlankCheck,
      totalLengthCheck,
      hasAtSymbolCheck,
      hasDotSymbolInDomainCheck,
      hasTextAfterDotCheck,
      hasTextAfterAtSymbolButBeforeDotCheck,
      localLengthCheck,
      domainLengthCheck,
      localBlankCheck,
      domainBlankCheck
    ).mapN { case _ ⇒ trimmed }
  }
}
