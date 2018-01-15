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

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsSuccess, Json}

class CreateAccountHeaderSpec extends WordSpec with Matchers {

  "CreateAccountHeader" when {

    "parsing JSON" must {

      "accept timestamps in the correct format" in {
          def jsonString(timestampValue: String): String =
            s"""
             |{
             | "version" : "1",
             | "createdTimestamp" : $timestampValue,
             | "clientCode" : "client",
             | "requestCorrelationId": "${UUID.randomUUID()}"
             |}
          """.stripMargin

        Json.parse(jsonString("\"2017-11-23 20:33:37 GMT\"")).validate[CreateAccountHeader] shouldBe a[JsSuccess[_]]
        Json.parse(jsonString("\"2017-11-23 20:33:37\"")).validate[CreateAccountHeader] shouldBe a[JsError]
        Json.parse(jsonString("\"20:33:37 2017-11-23\"")).validate[CreateAccountHeader] shouldBe a[JsError]
        Json.parse(jsonString("\"2017/11/23 20:33:37 GMT\"")).validate[CreateAccountHeader] shouldBe a[JsError]
        Json.parse(jsonString("true")).validate[CreateAccountHeader] shouldBe a[JsError]
      }

    }

  }

}
