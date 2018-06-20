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

sealed trait ApiError {
  val code: String
  val message: String
}

object ApiError {

  implicit val writes: Writes[ApiError] = new Writes[ApiError] {

    override def writes(o: ApiError): JsValue = Json.parse(
      s"""{ "code" : "${o.code}", "message" : "${o.message}"}""".stripMargin)
  }

}

case class ApiErrorValidationError(code: String, message: String) extends ApiError

object ApiErrorValidationError {

  def apply(description: String): ApiErrorValidationError =
    ApiErrorValidationError("400", s"Invalid request, description: $description")
}

case class ApiErrorBackendError(code: String, message: String) extends ApiError

object ApiErrorBackendError {

  def apply(): ApiErrorBackendError =
    ApiErrorBackendError("500", "Server error")

  def applyWithParams(errorMessage: String): ApiErrorBackendError =
    ApiErrorBackendError("500", errorMessage)
}

