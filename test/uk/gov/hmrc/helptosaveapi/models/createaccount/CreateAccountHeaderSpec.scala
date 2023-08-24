/*
 * Copyright 2023 HM Revenue & Customs
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

import java.util.UUID

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}

class CreateAccountHeaderSpec extends AnyWordSpec with Matchers {

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

        List(
          "\"2017-11-23 20:33:37\"",
          "\"20:33:37 2017-11-23\"",
          "\"2017/11/23 20:33:37 GMT\"",
          "true"
        ).foreach(s => Json.parse(jsonString(s)).validate[CreateAccountHeader] shouldBe a[JsError])
      }

    }

  }

}
