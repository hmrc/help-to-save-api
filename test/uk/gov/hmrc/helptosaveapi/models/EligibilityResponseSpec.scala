/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class EligibilityResponseSpec extends TestSupport {

  "EligibilityResponse" when {

    "writing eligibility responses" must {

      "write to json as expected when the response type is ApiEligibilityResponse" in {
        val expected = """{"eligibility":{"isEligible":true,"hasWTC":false,"hasUC":true},"accountExists":false}"""

        Json.toJson(
          ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), accountExists = false)
        ) shouldBe Json.parse(expected)
      }

      "write to json as expected when the response type is AccountAlreadyExists" in {
        Json.toJson(AccountAlreadyExists()) shouldBe Json.parse("""{"accountExists":true}""")
      }
    }

  }

}
