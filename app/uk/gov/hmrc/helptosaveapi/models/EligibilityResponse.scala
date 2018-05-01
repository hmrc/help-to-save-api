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

import play.api.libs.json._

sealed trait EligibilityResponse

object EligibilityResponse {

  implicit val writes: Writes[EligibilityResponse] = new Writes[EligibilityResponse] {
    override def writes(response: EligibilityResponse): JsValue = {

      val json = response match {
        case a: ApiEligibilityResponse ⇒
          s"""{"eligibility":
             |  { "isEligible": ${a.eligibility.isEligible},
             |    "hasWTC": ${a.eligibility.hasWTC},
             |    "hasUC": ${a.eligibility.hasUC}
             |  },
             |  "accountExists":  ${a.accountExists}
             | }""".stripMargin
        case b: AccountAlreadyExists ⇒
          s"""{"accountExists": ${b.accountExists}}"""
      }

      Json.parse(json)
    }
  }
}

case class ApiEligibilityResponse(eligibility: Eligibility, accountExists: Boolean) extends EligibilityResponse

case class AccountAlreadyExists(accountExists: Boolean = true) extends EligibilityResponse

case class Eligibility(isEligible: Boolean, hasWTC: Boolean, hasUC: Boolean)
