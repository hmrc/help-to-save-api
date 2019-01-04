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

case class ApiValidationError(code: String, message: String) extends ApiError

object ApiValidationError {

  def apply(description: String): ApiValidationError =
    ApiValidationError("VALIDATION_ERROR", s"Invalid request: $description")
}

case class ApiBackendError(code: String, message: String) extends ApiError

object ApiBackendError {

  def apply(): ApiBackendError =
    ApiBackendError("SERVER_ERROR", "Server error")
}

case class ApiAccessError(code: String, message: String) extends ApiError

object ApiAccessError {

  def apply(): ApiAccessError =
    ApiAccessError("ACCESS_ERROR", "access not allowed")
}
