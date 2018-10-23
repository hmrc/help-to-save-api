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

package uk.gov.hmrc.helptosaveapi.models.createaccount

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.Writes.temporalWrites
import play.api.libs.json._
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody.{BankDetails, ContactDetails}

case class CreateAccountBody(
    nino:                String,
    forename:            String,
    surname:             String,
    dateOfBirth:         LocalDate,
    contactDetails:      ContactDetails,
    registrationChannel: String,
    nbaDetails:          Option[BankDetails],
    systemId:            String
)

object CreateAccountBody {

  case class BankDetails(
      accountNumber: String,
      sortCode:      String,
      accountName:   String,
      rollNumber:    Option[String]
  )

  object BankDetails {
    implicit val format: Format[BankDetails] = Json.format[BankDetails]
  }

  case class ContactDetails(
      address1:                String,
      address2:                String,
      address3:                Option[String],
      address4:                Option[String],
      address5:                Option[String],
      postcode:                String,
      countryCode:             Option[String],
      communicationPreference: String,
      phoneNumber:             Option[String],
      email:                   Option[String])

  object ContactDetails {

    implicit val format: Format[ContactDetails] = Json.format[ContactDetails]

  }

  implicit val dateFormat: Format[LocalDate] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    Format[LocalDate](localDateReads(formatter), temporalWrites[LocalDate, DateTimeFormatter](formatter))
  }

  implicit val writes: Writes[CreateAccountBody] = Json.writes[CreateAccountBody]

  def reads(clientCode: String): Reads[CreateAccountBody] = Reads[CreateAccountBody]{ jsValue ⇒
    for {
      nino ← (jsValue \ "nino").validate[String]
      forename ← (jsValue \ "forename").validate[String]
      surname ← (jsValue \ "surname").validate[String]
      dateOfBirth ← (jsValue \ "dateOfBirth").validate[LocalDate]
      contactDetails ← (jsValue \ "contactDetails").validate[ContactDetails]
      registrationChannel ← (jsValue \ "registrationChannel").validate[String]
      nbaDetails ← (jsValue \ "bankDetails").validateOpt[BankDetails]
      systemId ← JsSuccess("MDTP-API-" + clientCode)
    } yield CreateAccountBody(nino, forename, surname, dateOfBirth, contactDetails, registrationChannel, nbaDetails, systemId)
  }

}
