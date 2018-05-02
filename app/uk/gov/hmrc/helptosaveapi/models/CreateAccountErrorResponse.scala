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

import play.api.libs.json.{Format, JsValue, Json}

case class CreateAccountErrorResponse(errorMessageId: String, errorMessage: String, errorDetails: String)

object CreateAccountErrorResponse {

  def apply(errorMessage: String, errorDetails: String): CreateAccountErrorResponse =
    CreateAccountErrorResponse("", errorMessage, errorDetails)

  implicit val format: Format[CreateAccountErrorResponse] = Json.format[CreateAccountErrorResponse]

  implicit class CreateAccountErrorResponseOps(val errorResponse: CreateAccountErrorResponse) extends AnyVal {
    def toJson(): JsValue = format.writes(errorResponse)
  }

}