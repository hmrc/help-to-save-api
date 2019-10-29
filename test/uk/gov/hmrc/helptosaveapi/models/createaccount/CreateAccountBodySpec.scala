/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody.{BankDetails, ContactDetails}
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class CreateAccountBodySpec extends TestSupport {

  "CreateAccountBody" when {

    "parsing JSON" must {

      "accept dates in the correct format" in {
          def jsonString(dobValue: String): String =
            s"""{
             | "nino" : "nino",
             | "forename" : "name",
             | "surname" : "surname",
             | "dateOfBirth" : $dobValue,
             | "contactDetails" : {
             |     "address1" : "1",
             |     "address2" : "2",
             |     "address3" : "3",
             |     "address4" : "4",
             |     "address5" : "5",
             |     "postcode": "postcode",
             |     "countryCode" : "country",
             |     "communicationPreference" : "preference",
             |     "email" : "email"
             | },
             | "registrationChannel" : "channel",
             | "bankDetails": {
             |    "accountNumber": "123",
             |    "sortCode": "123456",
             |    "accountName": "accountName",
             |    "rollNumber": "rollNumber"
             |  }
             |}
           """.stripMargin

        val reads = CreateAccountBody.reads("code")

        Json.parse(jsonString("\"19920423\"")).validate[CreateAccountBody](reads) shouldBe JsSuccess(
          CreateAccountBody(
            "nino", "name", "surname",
            LocalDate.of(1992, 4, 23),
            ContactDetails("1", "2", Some("3"), Some("4"), Some("5"), "postcode", Some("country"), "preference", None, Some("email")),
            "channel", Some(BankDetails("123", "123456", "accountName", Some("rollNumber"))),
            "MDTP-API-code"
          ))

        List(
          "\"1992-04-23\"",
          "\"23041992\"",
          "\"23-04-1992\"",
          "true"
        ).foreach(s ⇒
            Json.parse(jsonString(s)).validate[CreateAccountBody](reads) shouldBe a[JsError]
          )
      }

      "strip out any spaces in the sortcode" in {
        val str = ""
        val jsonString: String =
          s"""$str{
             | "nino" : "nino",
             | "forename" : "name",
             | "surname" : "surname",
             | "dateOfBirth" : "19920423",
             | "contactDetails" : {
             |     "address1" : "1",
             |     "address2" : "2",
             |     "address3" : "3",
             |     "address4" : "4",
             |     "address5" : "5",
             |     "postcode": "postcode",
             |     "countryCode" : "country",
             |     "communicationPreference" : "preference",
             |     "email" : "email"
             | },
             | "registrationChannel" : "channel",
             | "bankDetails": {
             |    "accountNumber": "123",
             |    "sortCode": "12 34 56",
             |    "accountName": "accountName",
             |    "rollNumber": "rollNumber"
             |  }
             |}
           """.stripMargin

        val reads = CreateAccountBody.reads("code")

        Json.parse(jsonString).validate[CreateAccountBody](reads) shouldBe JsSuccess(
          CreateAccountBody(
            "nino", "name", "surname",
            LocalDate.of(1992, 4, 23),
            ContactDetails("1", "2", Some("3"), Some("4"), Some("5"), "postcode", Some("country"), "preference", None, Some("email")),
            "channel", Some(BankDetails("123", "123456", "accountName", Some("rollNumber"))),
            "MDTP-API-code"
          ))
      }

      "strip out any hyphens in the sortcode" in {
        val str = ""
        val jsonString: String =
          s"""$str{
             | "nino" : "nino",
             | "forename" : "name",
             | "surname" : "surname",
             | "dateOfBirth" : "19920423",
             | "contactDetails" : {
             |     "address1" : "1",
             |     "address2" : "2",
             |     "address3" : "3",
             |     "address4" : "4",
             |     "address5" : "5",
             |     "postcode": "postcode",
             |     "countryCode" : "country",
             |     "communicationPreference" : "preference",
             |     "email" : "email"
             | },
             | "registrationChannel" : "channel",
             | "bankDetails": {
             |    "accountNumber": "123",
             |    "sortCode": "12-34-56",
             |    "accountName": "accountName",
             |    "rollNumber": "rollNumber"
             |  }
             |}
           """.stripMargin

        val reads = CreateAccountBody.reads("code")

        Json.parse(jsonString).validate[CreateAccountBody](reads) shouldBe JsSuccess(
          CreateAccountBody(
            "nino", "name", "surname",
            LocalDate.of(1992, 4, 23),
            ContactDetails("1", "2", Some("3"), Some("4"), Some("5"), "postcode", Some("country"), "preference", None, Some("email")),
            "channel", Some(BankDetails("123", "123456", "accountName", Some("rollNumber"))),
            "MDTP-API-code"
          ))
      }

    }

  }

}
