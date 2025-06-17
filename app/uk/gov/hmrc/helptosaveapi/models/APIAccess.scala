/*
 * Copyright 2025 HM Revenue & Customs
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

import com.typesafe.config.Config
import play.api.libs.json.{Json, Reads, Writes}

case class APIAccess(`type`: String)

object APIAccess {

  implicit val apiAccessWrites: Writes[APIAccess] = Json.writes[APIAccess]
  implicit val apiAccessReads: Reads[APIAccess] = Json.reads[APIAccess]

  type Version = String

  def apply(config: Config)(version: Version): APIAccess =
    APIAccess(
      `type` = config.getString(s"version-$version.type")
    )
}
