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

package uk.gov.hmrc.helptosaveapi.services

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import cats.data.Validated
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody.ContactDetails
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class CreateAccountRequestValidatorImplSpec extends TestSupport {

  val validator = new CreateAccountRequestValidatorImpl

  val validCreateAccountHeader: CreateAccountHeader = CreateAccountHeader("1.0", ZonedDateTime.now(), "KCOM", UUID.randomUUID())

  val validCreateAccountBody: CreateAccountBody = CreateAccountBody("", "", "", LocalDate.now(), ContactDetails("", "", None, None, None, "", None, "00"), "callCentre")

  val validCreateAccountRequest: CreateAccountRequest = CreateAccountRequest(
    validCreateAccountHeader,
    validCreateAccountBody
  )

  "CreateAccountRequestValidatorImpl" must {

    "mark as valid requests which are valid" in {
      validator.validateRequest(validCreateAccountRequest) shouldBe Validated.Valid(validCreateAccountRequest)

    }

    "mark as invalid requests" which {

      "have a header" which {

        "has an invalid version" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader.copy(version = "version"),
              validCreateAccountBody
            )).isInvalid shouldBe true

        }

        "has an invalid client code" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader.copy(clientCode = "a"),
              validCreateAccountBody
            )).isInvalid shouldBe true
        }
      }

      "have a body" which {

        "has an invalid registration channel" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(registrationChannel = "channel")
            )).isInvalid shouldBe true

        }

        "has an invalid communication preference" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(contactDetails = validCreateAccountBody.contactDetails.copy(communicationPreference = "comm"))
            )).isInvalid shouldBe true
        }
      }

    }

  }

}
