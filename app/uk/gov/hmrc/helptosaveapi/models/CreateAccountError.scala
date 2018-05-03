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

sealed trait CreateAccountError

case class CreateAccountValidationError(errorMessageId: String, errorMessage: String, errorDetails: String) extends CreateAccountError

object CreateAccountValidationError {

  def apply(errorMessage: String, errorDetails: String): CreateAccountValidationError =
    CreateAccountValidationError("", errorMessage, errorDetails)

  implicit val format: Format[CreateAccountValidationError] = Json.format[CreateAccountValidationError]

  implicit class CreateAccountValidationErrorOps(val errorResponse: CreateAccountValidationError) extends AnyVal {
    def toJson(): JsValue = format.writes(errorResponse)
  }

}

case class CreateAccountBackendError(errorMessageId: String, errorMessage: String, errorDetails: String) extends CreateAccountError

object CreateAccountBackendError {

  def apply(): CreateAccountBackendError =
    CreateAccountBackendError("", "server error", "")

  implicit val format: Format[CreateAccountBackendError] = Json.format[CreateAccountBackendError]

  implicit class CreateAccountBackendErrorOps(val errorResponse: CreateAccountBackendError) extends AnyVal {
    def toJson(): JsValue = format.writes(errorResponse)
  }

}
