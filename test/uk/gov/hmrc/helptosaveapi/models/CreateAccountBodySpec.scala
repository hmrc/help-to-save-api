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

import play.api.libs.json.{JsError, JsSuccess, Json}
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
             |     "postcode": "postcode",
             |     "countryCode" : "country",
             |     "communicationPreference" : "preference"
             | },
             | "registrationChannel" : "channel"
             |}
           """.stripMargin

        Json.parse(jsonString("\"19920423\"")).validate[CreateAccountBody] shouldBe a[JsSuccess[_]]
        Json.parse(jsonString("\"1992-04-23\"")).validate[CreateAccountBody] shouldBe a[JsError]
        Json.parse(jsonString("\"23041992\"")).validate[CreateAccountBody] shouldBe a[JsError]
        Json.parse(jsonString("\"23-04-1992\"")).validate[CreateAccountBody] shouldBe a[JsError]
        Json.parse(jsonString("true")).validate[CreateAccountBody] shouldBe a[JsError]
      }

    }

  }

}
