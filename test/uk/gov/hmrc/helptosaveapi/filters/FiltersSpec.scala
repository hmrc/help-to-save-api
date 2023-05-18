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

package uk.gov.hmrc.helptosaveapi.filters

import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class FiltersSpec extends TestSupport {

  "The Filters" must {

    "add a 'X-XSS-Protection' header to all requests" in {
      val result = route(fakeApplication, FakeRequest(GET, "/test")).get
      headers(result) should contain(X_XSS_PROTECTION -> "1; mode=block")
    }

    "add a 'X-Content-Type-Options' header to all requests" in {
      val result = route(fakeApplication, FakeRequest(GET, "/test")).get
      headers(result) should contain(X_CONTENT_TYPE_OPTIONS -> "nosniff")
    }
  }
}
