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

package uk.gov.hmrc.helptosaveapi.validators

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody.ContactDetails
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}
import uk.gov.hmrc.helptosaveapi.util.TestSupport

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

class CreateAccountRequestValidatorSpec extends TestSupport with ScalaCheckPropertyChecks {

  val validator = new CreateAccountRequestValidator(new EmailValidation(config))

  val validCreateAccountHeader: CreateAccountHeader =
    CreateAccountHeader("1.0", ZonedDateTime.now(), "KCOM", UUID.randomUUID())

  val validCreateAccountBody: CreateAccountBody =
    CreateAccountBody(
      "",
      "forename",
      "surname",
      LocalDate.now(),
      ContactDetails("", "", None, None, None, "", None, "00", Some("07841000000"), Some("test@gmail.com")),
      "callCentre",
      None,
      "systemId"
    )

  val validCreateAccountRequest: CreateAccountRequest = CreateAccountRequest(
    validCreateAccountHeader,
    validCreateAccountBody
  )

  "CreateAccountRequestValidator" must {

    "mark as valid requests" which {

      def testIsValid(request: CreateAccountRequest): Unit =
        validator.validateRequest(request).isValid shouldBe true

      "are valid" in {
        testIsValid(validCreateAccountRequest)
      }

      "has a forename containing a special character" in {
        testIsValid(
          CreateAccountRequest(
            validCreateAccountHeader,
            validCreateAccountBody.copy(forename = "fo-ren&a.me")
          )
        )
      }

      "has a surname containing a special character" in {
        testIsValid(
          CreateAccountRequest(
            validCreateAccountHeader,
            validCreateAccountBody.copy(surname = "sur-n&ame")
          )
        )
      }

      "has an empty phone number" in {
        testIsValid(
          CreateAccountRequest(
            validCreateAccountHeader,
            validCreateAccountBody.copy(contactDetails = validCreateAccountBody.contactDetails.copy(phoneNumber = None))
          )
        )
      }

      "has an phone number with allowed special characters" in {
        validator.allowedPhoneNumberSpecialCharacters.foreach { c =>
          testIsValid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody
                .copy(contactDetails = validCreateAccountBody.contactDetails.copy(phoneNumber = Some(s"1$c")))
            )
          )
        }
      }

    }

    "mark as invalid requests" which {

      def testIsInvalid(request: CreateAccountRequest): Unit =
        validator.validateRequest(request).isInvalid shouldBe true

      "have a phone number" which {

        "does not contain any digits" in {
          validator.allowedPhoneNumberSpecialCharacters.foreach { c =>
            testIsInvalid(
              CreateAccountRequest(
                validCreateAccountHeader,
                validCreateAccountBody
                  .copy(contactDetails = validCreateAccountBody.contactDetails.copy(phoneNumber = Some(c.toString)))
              )
            )
          }
        }

        "contains disallowed characters" in {
          forAll { (c: Char) =>
            whenever(!c.isDigit && !validator.allowedPhoneNumberSpecialCharacters.contains(c)) {
              testIsInvalid(
                CreateAccountRequest(
                  validCreateAccountHeader,
                  validCreateAccountBody
                    .copy(contactDetails = validCreateAccountBody.contactDetails.copy(phoneNumber = Some(s"1$c")))
                )
              )
            }
          }
        }
      }

      "have a header" which {

        "has an invalid version" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader.copy(version = "version"),
              validCreateAccountBody
            )
          )

        }

        "has an invalid version length" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader.copy(version = "1.11111111111111111111111"),
              validCreateAccountBody
            )
          )

        }

        "has an invalid client code" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader.copy(clientCode = "a"),
              validCreateAccountBody
            )
          )
        }

        "has an invalid client code length" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader.copy(clientCode = "abcdefghijklmnopqrstuvwxyz"),
              validCreateAccountBody
            )
          )
        }
      }

      "have a body" which {

        "has an invalid registration channel" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(registrationChannel = "channel")
            )
          )

        }

        "has an invalid communication preference" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody
                .copy(contactDetails = validCreateAccountBody.contactDetails.copy(communicationPreference = "comm"))
            )
          )
        }

        "has a forename starting with a special character" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "&forename")
            )
          )
        }

        "has a forename containing an apostrophe" in {
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "fore'name")
            )
          )
          result.isInvalid shouldBe true
          result shouldBe Invalid(NonEmptyList.of("forename contains an apostrophe"))
        }

        "has a forename with no more than one consecutive special character" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(forename = "fore--name")
            )
          )
        }

        "has a surname starting with a special character" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "-surname")
            )
          )
        }

        "has a surname ending with a special character" in {
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "surname-")
            )
          )

          result.isInvalid shouldBe true
          result.leftSideValue.toString shouldBe "Invalid(NonEmptyList(surname ended with special character))"
        }

        "has a surname must be at least one letter" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "")
            )
          )
        }

        "has a surname with no more than one consecutive special character" in {
          testIsInvalid(
            CreateAccountRequest(
              validCreateAccountHeader,
              validCreateAccountBody.copy(surname = "sur--name")
            )
          )
        }

        "has a forename containing a digit" in {
          validator
            .validateRequest(
              CreateAccountRequest(
                validCreateAccountHeader,
                validCreateAccountBody.copy(forename = "fore123name")
              )
            )
            .isInvalid shouldBe true
        }

        "has a surname containing a digit" in {
          validator
            .validateRequest(
              CreateAccountRequest(
                validCreateAccountHeader,
                validCreateAccountBody.copy(surname = "surname123")
              )
            )
            .isInvalid shouldBe true
        }

        "have a missing email when communicationPreference is 02" in {
          val body = validCreateAccountBody.copy(
            contactDetails = validCreateAccountBody.contactDetails.copy(communicationPreference = "02")
          )
          val bodyWithNoEmail = body.copy(contactDetails = body.contactDetails.copy(email = None))
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              bodyWithNoEmail
            )
          )

          result.isInvalid shouldBe true
          result shouldBe Invalid(NonEmptyList.of("invalid email provided with communicationPreference = 02"))
        }

        "have invalid email when communicationPreference is 02" in {
          val body = validCreateAccountBody.copy(
            contactDetails = validCreateAccountBody.contactDetails.copy(communicationPreference = "02")
          )
          val bodyWithNoEmail = body.copy(contactDetails = body.contactDetails.copy(email = Some("invalidEmail")))
          val result = validator.validateRequest(
            CreateAccountRequest(
              validCreateAccountHeader,
              bodyWithNoEmail
            )
          )
          result.isInvalid shouldBe true
          result shouldBe Invalid(NonEmptyList.of("invalid email provided with communicationPreference = 02"))
        }
      }

    }

  }

}
