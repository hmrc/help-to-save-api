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

package uk.gov.hmrc.helptosaveapi.models

import play.api.libs.json.{Format, Json}

case class Account(
  accountNumber: String,
  headroom: BigDecimal,
  closed: Boolean,
  blockedFromPayment: Boolean,
  balance: BigDecimal,
  bonusTerms: Seq[BonusTerm],
  nbaAccountNumber: Option[String] = None,
  nbaPayee: Option[String] = None,
  nbaRollNumber: Option[String] = None,
  nbaSortCode: Option[String] = None
)

object Account {

  implicit val format: Format[Account] = Json.format[Account]

  def fromHtsAccount(account: HtsAccount): Account =
    Account(
      account.accountNumber,
      account.canPayInThisMonth,
      account.isClosed,
      false,
      account.balance,
      account.bonusTerms.map(BonusTerm.fromHtsBonusTerm),
      account.nbaAccountNumber,
      account.nbaPayee,
      account.nbaRollNumber,
      account.nbaSortCode
    )

}
