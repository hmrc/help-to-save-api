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

package uk.gov.hmrc.helptosaveapi.models.createaccount

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import cats.Show
import play.api.libs.json.Reads.zonedDateTimeReads
import play.api.libs.json.Writes.temporalWrites
import play.api.libs.json._

case class CreateAccountHeader(
    version:              String,
    createdTimestamp:     ZonedDateTime,
    clientCode:           String,
    requestCorrelationId: UUID)

object CreateAccountHeader {

  implicit val localDateTimeFormat: Format[ZonedDateTime] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
    Format[ZonedDateTime](zonedDateTimeReads(formatter), temporalWrites[ZonedDateTime, DateTimeFormatter](formatter))
  }

  implicit val format: Format[CreateAccountHeader] = Json.format[CreateAccountHeader]

  implicit val show: Show[CreateAccountHeader] =
    Show.show(createAccountHeader ⇒
      s"""{version: ${createAccountHeader.version},
          createdTimestamp: ${createAccountHeader.createdTimestamp},
          clientCode: ${createAccountHeader.clientCode},
          requestCorrelationId: ${createAccountHeader.requestCorrelationId}
          }"""
    )
}
