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

package uk.gov.hmrc.helptosaveapi.util

import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}

import org.scalacheck.Gen
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody.ContactDetails
import uk.gov.hmrc.helptosaveapi.models.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest}

object DataGenerators {

  def random[A](gen: Gen[A]): A = gen.sample.getOrElse(sys.error("Could not generate data"))

  val createAccountHeaderGen: Gen[CreateAccountHeader] =
    for {
      version ← Gen.identifier
      date ← Gen.choose(0L, 100L).map(t ⇒ ZonedDateTime.ofInstant(Instant.ofEpochSecond(t), ZoneId.of("UTC")))
      clientCode ← Gen.identifier
      correlationId ← Gen.uuid
    } yield CreateAccountHeader(version, date, clientCode, correlationId)

  val contactDetailsGen: Gen[ContactDetails] =
    for {
      line1 ← Gen.identifier
      line2 ← Gen.identifier
      line3 ← Gen.option(Gen.identifier)
      line4 ← Gen.option(Gen.identifier)
      line5 ← Gen.option(Gen.identifier)
      postcode ← Gen.identifier
      countryCode ← Gen.option(Gen.alphaStr)
      communicationPreference ← Gen.alphaStr
    } yield ContactDetails(line1, line2, line3, line4, line5, postcode, countryCode, communicationPreference)

  val createAccountBodyGen: Gen[CreateAccountBody] =
    for {
      nino ← Gen.identifier
      name ← Gen.alphaStr
      surname ← Gen.alphaStr
      dob ← Gen.choose(1L, 100L).map(LocalDate.ofEpochDay)
      contactDetails ← contactDetailsGen
      registrationChannel ← Gen.alphaStr
    } yield CreateAccountBody(nino, name, surname, dob, contactDetails, registrationChannel)

  val createAccountRequestGen: Gen[CreateAccountRequest] =
    for {
      header ← createAccountHeaderGen
      body ← createAccountBodyGen
    } yield CreateAccountRequest(header, body)

}