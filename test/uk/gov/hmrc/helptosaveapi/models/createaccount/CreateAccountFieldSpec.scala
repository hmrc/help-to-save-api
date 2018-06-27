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

package uk.gov.hmrc.helptosaveapi.models.createaccount

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountField.{AddressLine1, AddressLine5, _}

class CreateAccountFieldSpec extends WordSpec with Matchers {

  import CreateAccountFieldSpec._

  "CreateAccountField" must {

    "have a method" which {

      val validContactDetails = TestContactDetails(Some("line1"), Some("line2"), Some("postcode"))

      val validBody = TestCreateAccountBody(Some("name"), Some("surname"), Some("dob"), Some("nino"), Some("channel"), validContactDetails)

      val validRequest = TestCreateAccountRequest(validBody)

      "returns missing mandatory fields" when {

        "no mandatory fields are missing" in {
          CreateAccountField.missingMandatoryFields(validRequest.toJson()) shouldBe Set.empty
        }

        "forename is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(forename = None)).toJson()
          ) shouldBe Set(Forename)
        }

        "surname is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(surname = None)).toJson()
          ) shouldBe Set(Surname)
        }

        "date of birth is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(dateOfBirth = None)).toJson()
          ) shouldBe Set(DateOfBirth)
        }

        "nino is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(nino = None)).toJson()
          ) shouldBe Set(NINO)
        }

        "address1 is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(contactDetails = validContactDetails.copy(address1 = None))).toJson()
          ) shouldBe Set(CreateAccountField.Address)
        }

        "address2 is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(contactDetails = validContactDetails.copy(address2 = None))).toJson()
          ) shouldBe Set(CreateAccountField.Address)
        }

        "postcode is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(contactDetails = validContactDetails.copy(postcode = None))).toJson()
          ) shouldBe Set(CreateAccountField.Address)
        }

        "registration channel is missing" in {
          CreateAccountField.missingMandatoryFields(
            validRequest.copy(body = validBody.copy(registrationChannel = None)).toJson()
          ) shouldBe Set(RegistrationChannel)
        }

        "multiple mandatory fields are missing" in {
          CreateAccountField.missingMandatoryFields(TestCreateAccountRequest.empty().toJson) shouldBe
            Set[MandatoryCreateAccountField](Forename, Surname, DateOfBirth, NINO, RegistrationChannel, CreateAccountField.Address)
        }

      }

      "inserts fields" in {
        val json = Json.parse("""{"a" : 1, "body" :{ "thing" : 1 } }""")

        CreateAccountField.insertFields(
          Map(
            Forename → JsString("name"),
            Surname → JsString("surname"),
            DateOfBirth → JsString("dob"),
            NINO → JsString("nino"),
            AddressLine1 → JsString("address1"),
            AddressLine2 → JsString("address2"),
            AddressLine3 → JsString("address3"),
            AddressLine4 → JsString("address4"),
            AddressLine5 → JsString("address5"),
            Postcode → JsString("postcode"),
            CountryCode → JsString("countryCode"),
            Email → JsString("email")
          ))(json) shouldBe Json.parse(
            """
            |{
            |  "a" : 1,
            |  "body" : {
            |    "thing"       : 1,
            |    "forename"    : "name",
            |    "surname"     : "surname",
            |    "dateOfBirth" : "dob",
            |    "nino"        : "nino",
            |    "contactDetails" : {
            |      "address1" : "address1",
            |      "address2" : "address2",
            |      "address3" : "address3",
            |      "address4" : "address4",
            |      "address5" : "address5",
            |      "postcode" : "postcode",
            |      "countryCode" : "countryCode",
            |      "email" : "email"
            |    }
            |  }
            |}
          """.stripMargin)

        CreateAccountField.insertFields(
          Map(
            RegistrationChannel → JsString("channel"),
            CreateAccountField.Address → JsString("address")
          ))(json) shouldBe json

      }

    }

  }

}

object CreateAccountFieldSpec {

  case class TestContactDetails(
      address1: Option[String],
      address2: Option[String],
      postcode: Option[String]
  )

  case class TestCreateAccountBody(
      forename:            Option[String],
      surname:             Option[String],
      dateOfBirth:         Option[String],
      nino:                Option[String],
      registrationChannel: Option[String],
      contactDetails:      TestContactDetails
  )

  case class TestCreateAccountRequest(body: TestCreateAccountBody)
  object TestCreateAccountRequest {

    def empty(): TestCreateAccountRequest =
      TestCreateAccountRequest(TestCreateAccountBody(None, None, None, None, None, TestContactDetails(None, None, None)))

    implicit val contactDetailsFormat: Format[TestContactDetails] = Json.format[TestContactDetails]
    implicit val createAccountBodyFormat: Format[TestCreateAccountBody] = Json.format[TestCreateAccountBody]
    implicit val createAccountRequestFormat: Format[TestCreateAccountRequest] = Json.format[TestCreateAccountRequest]

    implicit class TestCreateAccountRequestOps(val r: TestCreateAccountRequest) extends AnyVal {

      def toJson(): JsValue = Json.toJson(r)

      def withForename(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(forename = s))

      def withSurname(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(surname = s))

      def withDateOfBirth(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(dateOfBirth = s))

      def withNINO(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(nino = s))

      def withRegistrationChannel(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(registrationChannel = s))

      def withContactDetails(c: TestContactDetails): TestCreateAccountRequest =
        r.copy(body = r.body.copy(contactDetails = c))

      def withAddress1(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(contactDetails = r.body.contactDetails.copy(address1 = s)))

      def withAddress2(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(contactDetails = r.body.contactDetails.copy(address2 = s)))

      def withPostcode(s: Option[String]): TestCreateAccountRequest =
        r.copy(body = r.body.copy(contactDetails = r.body.contactDetails.copy(postcode = s)))

    }

  }

}
