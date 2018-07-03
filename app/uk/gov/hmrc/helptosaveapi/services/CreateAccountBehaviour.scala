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

package uk.gov.hmrc.helptosaveapi.services

import cats.instances.option._
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.eq._
import play.api.libs.json.{JsString, JsValue, Writes}
import uk.gov.hmrc.helptosaveapi.models.{ApiAccessError, ApiBackendError, ApiError, ApiValidationError}
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountField._
import uk.gov.hmrc.helptosaveapi.models.createaccount._

private[services] trait CreateAccountBehaviour { this: HelpToSaveApiService ⇒

  def fillInMissingDetailsGG(json:                 JsValue,
                             missingDetails:       Set[MandatoryCreateAccountField],
                             retrievedUserDetails: RetrievedUserDetails
  ): Either[ApiError, JsValue] = {
    import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountRequest._

    (json.registrationChannel(), json.nino(), retrievedUserDetails.nino) match {
      case (None, _, _) ⇒
        Left(ApiValidationError("No registration channel was given", ""))

      case (Some(JsString(registrationChannel)), Some(JsString(bodyNINO)), Some(retrievedNINO)) ⇒
        if (bodyNINO =!= retrievedNINO) {
          Left(ApiAccessError())
        } else {
          collate(json, missingDetails, retrievedUserDetails, retrievedNINO, registrationChannel)
        }

      case (Some(JsString(registrationChannel)), None, Some(nino)) ⇒
        collate(json, missingDetails, retrievedUserDetails, nino, registrationChannel)

      case (_, _, None) ⇒
        Left(ApiAccessError())
    }

  }

  private def collate(json:                 JsValue, // scalastyle:ignore
                      missingDetails:       Set[MandatoryCreateAccountField],
                      retrievedUserDetails: RetrievedUserDetails,
                      nino:                 String,
                      registrationChannel:  String
  ): Either[ApiError, JsValue] = {
    import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountRequest._

      def toJsValue[A](kv: A)(implicit writes: Writes[A]): JsValue =
        writes.writes(kv)

    val collatedData: List[(CreateAccountField, Option[JsValue])] = {
      val communicationPreference: (CreateAccountField, Option[JsValue]) =
        json.communicationPreference().fold{
          if (registrationChannel === "online") {
            CommunicationPreference → Some(toJsValue("02"))
          } else if (registrationChannel === "callCentre") {
            CommunicationPreference → Some(toJsValue("00"))
          } else {
            CommunicationPreference → None
          }
        } (CommunicationPreference → Some(_))

      val email: List[(CreateAccountField, Option[JsValue])] =
        json.email().fold(
          if (registrationChannel === "online") {
            List(Email → retrievedUserDetails.email.map(toJsValue(_)))
          } else {
            List.empty
          }
        )(e ⇒ List(Email → Some(e)))

      communicationPreference :: email ::: missingDetails.toList.flatMap {
        case Forename ⇒
          List(Forename → retrievedUserDetails.forename.map(toJsValue(_)))

        case Surname ⇒
          List(Surname → retrievedUserDetails.surname.map(toJsValue(_)))

        case DateOfBirth ⇒
          List(DateOfBirth → retrievedUserDetails.dateOfBirth.map(CreateAccountBody.dateFormat.writes))

        case NINO ⇒
          List(NINO → Some(toJsValue(nino)))

        case CreateAccountField.Address ⇒
          val addressFields: Option[List[(CreateAccountField, Option[JsValue])]] =
            (retrievedUserDetails.address.line1,
              retrievedUserDetails.address.line2,
              retrievedUserDetails.address.postCode
            ).mapN {
                case (l1, l2, p) ⇒
                  List[(CreateAccountField, JsValue)](
                    AddressLine1 → toJsValue(l1),
                    AddressLine2 → toJsValue(l2),
                    Postcode → toJsValue(p),
                    AddressLine3 → toJsValue(retrievedUserDetails.address.line3),
                    AddressLine4 → toJsValue(retrievedUserDetails.address.line4),
                    AddressLine5 → toJsValue(retrievedUserDetails.address.line5),
                    CountryCode → toJsValue(retrievedUserDetails.address.countryCode)
                  ).map{ case (k, v) ⇒ k → Some(v) }
              }
          addressFields.getOrElse(List(CreateAccountField.Address → None))

        case RegistrationChannel ⇒
          List(RegistrationChannel → None)
      }
    }

    val (data, stillMissing) = {
      collatedData.collect{ case (field, Some(value)) ⇒ field → value }.toMap →
        collatedData.collect{ case (field, None) ⇒ field }
    }

    if (stillMissing.nonEmpty) {
      Left(ApiBackendError("MISSING_DATA", s"cannot retrieve data: [${stillMissing.mkString(", ")}]"))
    } else {
      Right(CreateAccountField.insertFields(data)(json))
    }
  }

}

