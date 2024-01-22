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

package uk.gov.hmrc.helptosaveapi.controllers

import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.controllers.DocumentationController.APIAccess

import scala.concurrent.Future
import scala.io.Source

class DocumentationControllerSpec extends TestSupport {

  val access: String = "PRIVATE"

  val configuration: Configuration = Configuration(
    "api.access.version-2.0.type"    -> access,
    "api.access.version-2.0.enabled" -> true
  )
  val controller = new DocumentationController(configuration, mockCc, mockAssets)

  val apiAccess: String => APIAccess = APIAccess(configuration.underlying.getConfig("api.access"))

  "definition" must {
    "return the definition txt file when called" in {
      val result: Future[Result] = controller.definition()(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        uk.gov.hmrc.helptosaveapi.views.txt.definition(apiAccess, _ === "2.0").body
      )
    }
  }

  "yaml" must {
    "return the yaml documentation when called" in {
      val result: Future[Result] = controller.yaml("2.0", "application.yaml")(FakeRequest())
      val raml =
        Source.fromInputStream(getClass().getResourceAsStream("/public/api/conf/2.0/application.yaml")).mkString
      status(result) shouldBe OK
      contentAsString(result) shouldBe raml
    }
  }

  "APIAccess" must {
    "write valid json" in {
      val expectedJson = Json.parse("""{"type":"PRIVATE"}""")
      val apiAccess = APIAccess(access)

      Json.toJson[APIAccess](apiAccess) shouldBe expectedJson
    }
  }
}
