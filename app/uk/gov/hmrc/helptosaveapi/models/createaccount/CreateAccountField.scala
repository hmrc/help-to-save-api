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

package uk.gov.hmrc.helptosaveapi.models.createaccount

import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.apply._
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.helptosaveapi.util.Validation._

import scala.annotation.tailrec

sealed trait CreateAccountField
sealed trait MandatoryCreateAccountField extends CreateAccountField

object CreateAccountField {

  case object Forename extends MandatoryCreateAccountField
  case object Surname extends MandatoryCreateAccountField
  case object DateOfBirth extends MandatoryCreateAccountField
  case object NINO extends MandatoryCreateAccountField
  case object RegistrationChannel extends MandatoryCreateAccountField
  case object Address extends MandatoryCreateAccountField
  case object AddressLine1 extends CreateAccountField
  case object AddressLine2 extends CreateAccountField
  case object AddressLine3 extends CreateAccountField
  case object AddressLine4 extends CreateAccountField
  case object AddressLine5 extends CreateAccountField
  case object Postcode extends CreateAccountField
  case object CountryCode extends CreateAccountField
  case object Email extends CreateAccountField
  case object CommunicationPreference extends CreateAccountField

  private type MandatoryCreateAccountFieldValidation[A] = ValidatedNel[MandatoryCreateAccountField, A]

  def missingMandatoryFields(json: JsValue): Set[MandatoryCreateAccountField] = {
    import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountRequest._

    val forename: MandatoryCreateAccountFieldValidation[JsValue] =
      validationFromBoolean(json)(_.forename().isDefined, _ ⇒ Forename)

    val surname: MandatoryCreateAccountFieldValidation[JsValue] =
      validationFromBoolean(json)(_.surname().isDefined, _ ⇒ Surname)

    val dob: MandatoryCreateAccountFieldValidation[JsValue] =
      validationFromBoolean(json)(_.dateOfBirth().isDefined, _ ⇒ DateOfBirth)

    val nino: MandatoryCreateAccountFieldValidation[JsValue] =
      validationFromBoolean(json)(_.nino().isDefined, _ ⇒ NINO)

    val address: MandatoryCreateAccountFieldValidation[JsValue] = {
      type V[A] = ValidatedNel[CreateAccountField, A]
      val address1: V[JsValue] =
        validationFromBoolean(json)(_.address1().isDefined, _ ⇒ AddressLine1)

      val address2: V[JsValue] =
        validationFromBoolean(json)(_.address2().isDefined, _ ⇒ AddressLine2)

      val postcode: V[JsValue] =
        validationFromBoolean(json)(_.postcode().isDefined, _ ⇒ Postcode)

      (address1, address2, postcode).mapN{ case _ ⇒ json }.leftMap(_ ⇒ NonEmptyList.one(Address))
    }

    val registrationChannel: MandatoryCreateAccountFieldValidation[JsValue] =
      validationFromBoolean(json)(_.registrationChannel().isDefined, _ ⇒ RegistrationChannel)

    (forename, surname, dob, nino, address, registrationChannel)
      .mapN{ case _ ⇒ Set.empty[MandatoryCreateAccountField] }
      .leftMap(_.toList.toSet)
      .merge
  }

  def insertFields(fields: Map[CreateAccountField, JsValue])(json: JsValue): JsValue = // scalastyle:ignore
    fields.foldLeft(json.as[JsObject]){
      case (acc, (field, value)) ⇒
        acc.deepMerge(field match {
          case Forename                      ⇒ jsObject(NonEmptyList.of("body", "forename") → value)
          case Surname                       ⇒ jsObject(NonEmptyList.of("body", "surname") → value)
          case DateOfBirth                   ⇒ jsObject(NonEmptyList.of("body", "dateOfBirth") → value)
          case NINO                          ⇒ jsObject(NonEmptyList.of("body", "nino") → value)
          case AddressLine1                  ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "address1") → value)
          case AddressLine2                  ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "address2") → value)
          case AddressLine3                  ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "address3") → value)
          case AddressLine4                  ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "address4") → value)
          case AddressLine5                  ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "address5") → value)
          case Postcode                      ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "postcode") → value)
          case CountryCode                   ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "countryCode") → value)
          case Email                         ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "email") → value)
          case CommunicationPreference       ⇒ jsObject(NonEmptyList.of("body", "contactDetails", "communicationPreference") → value)
          case RegistrationChannel | Address ⇒ JsObject(Map.empty[String, JsValue])
        })
    }

  private def jsObject(s: (NonEmptyList[String], JsValue)): JsObject = {

      @tailrec
      def loop(l: List[String], acc: JsObject): JsObject = l match {
        case Nil          ⇒ acc
        case head :: Nil  ⇒ JsObject(List(head → acc))
        case head :: tail ⇒ loop(tail, JsObject(List(head → acc)))
      }

    val reversed = s._1.reverse
    loop(reversed.tail, JsObject(List(reversed.head → s._2)))
  }

}
