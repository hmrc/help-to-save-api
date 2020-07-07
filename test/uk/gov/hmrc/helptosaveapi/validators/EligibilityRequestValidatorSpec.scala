/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.validators

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class EligibilityRequestValidatorSpec extends TestSupport {

  val validator = new EligibilityRequestValidator()

  "EligibilityRequestValidator" when {

    "validating the requests" must {

      val validNino = "AE123456C"
      val badNino = "AE123XXBLAH"

      "return with no errors if the nino confirms to regex" in {
        validator.validateNino(validNino) shouldBe Valid(validNino)
      }

      "return with errors if the nino doesnt confirm to regex" in {
        validator.validateNino(badNino) shouldBe Invalid(
          NonEmptyList(
            "NINO doesn't match the regex: ^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$",
            Nil
          )
        )
      }
    }
  }

}
