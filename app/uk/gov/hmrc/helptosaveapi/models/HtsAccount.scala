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

import java.time.LocalDate

import play.api.libs.json.{Format, Json}

case class BonusTerm(bonusEstimate:          BigDecimal,
                     bonusPaid:              BigDecimal,
                     endDate:                LocalDate,
                     bonusPaidOnOrAfterDate: LocalDate)

object BonusTerm {
  implicit val writes: Format[BonusTerm] = Json.format[BonusTerm]
}

case class Blocking(unspecified: Boolean)

object Blocking {
  implicit val writes: Format[Blocking] = Json.format[Blocking]
}

case class HtsAccount(accountNumber:          String,
                      isClosed:               Boolean,
                      blocked:                Blocking,
                      balance:                BigDecimal,
                      paidInThisMonth:        BigDecimal,
                      canPayInThisMonth:      BigDecimal,
                      maximumPaidInThisMonth: BigDecimal,
                      thisMonthEndDate:       LocalDate,
                      bonusTerms:             Seq[BonusTerm],
                      closureDate:            Option[LocalDate]  = None,
                      closingBalance:         Option[BigDecimal] = None)

object HtsAccount {
  implicit val format: Format[HtsAccount] = Json.format[HtsAccount]
}