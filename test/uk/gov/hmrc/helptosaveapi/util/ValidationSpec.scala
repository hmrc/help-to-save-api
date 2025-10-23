/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.util

import cats.data.{NonEmptyList, Validated}

class ValidationSpec extends TestSupport {

  "Validation" must {

    "have a method which can validate based on a boolean predicate" in {
      val i = 1
      def validation[A] = Validation.validationFromBoolean[Int, String](i)

      validation(_ < 0, _ => "uh oh") shouldBe Validated.Invalid(NonEmptyList.of("uh oh"))
      validation(_ > 0, _ => "uh oh") shouldBe Validated.Valid(i)
    }

  }

}
