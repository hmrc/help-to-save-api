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

package uk.gov.hmrc.helptosaveapi.filters

import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.util.TestSupport

import scala.concurrent.{ExecutionContext, Future}

class XSSProtectionFilterSpec extends TestSupport {

  "The XSSProtectionFilter" must {

    "add a 'X-XSS-Protection' header to all requests" in {
      implicit val ec: ExecutionContext = fakeApplication.materializer.executionContext

      val filter = new XSSProtectionFilter(fakeApplication.materializer)
      val result = filter.apply(_ ⇒ Future.successful(Ok))(FakeRequest())
      headers(result) shouldBe Map("X-XSS-Protection" → "1; mode=block")

    }

  }

}
