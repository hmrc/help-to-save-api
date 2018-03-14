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

package uk.gov.hmrc.helptosaveapi.validators

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated}
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody.ContactDetails
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class CreateAccountRequestValidatorSpec extends TestSupport {

  val validator = new CreateAccountRequestValidator

  val validCreateAccountHeader: CreateAccountHeader = CreateAccountHeader("1.0.1.2.3", ZonedDateTime.now(), "KCOM", UUID.randomUUID())

  val validCreateAccountBody: CreateAccountBody =
    CreateAccountBody("", "forename", "surname", LocalDate.now(),
                                                 ContactDetails("", "", None, None, None, "", None, "00", Some("07841000000"), Some("test@gmail.com")), "callCentre"
    )

  val validCreateAccountRequest: CreateAccountRequest = CreateAccountRequest(
    validCreateAccountHeader,
    validCreateAccountBody
  )

  "CreateAccountRequestValidator" must {

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

        "has an invalid version length" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader.copy(version = "1.1.1.1.1.1.1.1.1.1.1.1.1.1.1"),
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

        "has an invalid client code length" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader.copy(clientCode = "abcdefghijklmnopqrstuvwxyz"),
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

        "has a forename containing a special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "fo-ren&a.me")
            )).isValid shouldBe true
        }

        "has a forename starting with a special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "&forename")
            )).isInvalid shouldBe true
        }

        "has a forename containing an apostrophe" in {
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "fore'name")
            ))

          result.isInvalid shouldBe true
          result.leftSideValue.toString shouldBe "Invalid(NonEmptyList(forename contains an apostrophe))"
        }

        "has a forename with no more than one consecutive special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "fore--name")
            )).isInvalid shouldBe true
        }

        "has a surname containing a special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "sur-n&ame")
            )).isValid shouldBe true
        }

        "has a surname starting with a special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "-surname")
            )).isInvalid shouldBe true
        }

        "has a surname ending with a special character" in {
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "surname-")
            ))

          result.isInvalid shouldBe true
          result.leftSideValue.toString shouldBe "Invalid(NonEmptyList(surname ended with special character))"
        }

        "has a surname must be at least one letter" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "")
            )).isInvalid shouldBe true
        }

        "has a surname with no more than one consecutive special character" in {
          validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "sur--name")
            )).isInvalid shouldBe true
        }
      }

    }

  }

}
