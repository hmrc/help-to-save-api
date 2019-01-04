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

package uk.gov.hmrc.helptosaveapi.validators

import java.util.regex.Matcher

import uk.gov.hmrc.helptosaveapi.util.ValidatedOrErrorString
import uk.gov.hmrc.helptosaveapi.util.Validation.validationFromBoolean

class EligibilityRequestValidator {

  private val ninoRegex: String = "^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$"

  private val pattern: String ⇒ Matcher = ninoRegex.r.pattern.matcher _

  def validateNino(nino: String): ValidatedOrErrorString[String] = {
    validationFromBoolean(nino)(_ ⇒ pattern(nino).matches(), _ ⇒ s"NINO doesn't match the regex: $ninoRegex")
  }

}
