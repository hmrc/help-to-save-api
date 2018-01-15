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

package uk.gov.hmrc.helptosaveapi.models

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.Writes.temporalWrites
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody.ContactDetails

case class CreateAccountBody(
    nino:                String,
    forename:            String,
    surname:             String,
    dateOfBirth:         LocalDate,
    contactDetails:      ContactDetails,
    registrationChannel: String
)

object CreateAccountBody {

  case class ContactDetails(
      address1:                String,
      address2:                String,
      address3:                Option[String],
      address4:                Option[String],
      address5:                Option[String],
      postcode:                String,
      countryCode:             Option[String],
      communicationPreference: String)

  object ContactDetails {

    implicit val format: Format[ContactDetails] = Json.format[ContactDetails]

  }

  implicit val dateFormat: Format[LocalDate] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    Format[LocalDate](localDateReads(formatter), temporalWrites[LocalDate, DateTimeFormatter](formatter))
  }

  implicit val format: Format[CreateAccountBody] = Json.format[CreateAccountBody]

}
