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

package uk.gov.hmrc.helptosaveapi.util

import org.scalacheck.Gen
import uk.gov.hmrc.auth.core.retrieve.ItmpAddress
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody.{BankDetails, ContactDetails}
import uk.gov.hmrc.helptosaveapi.models.createaccount.{CreateAccountBody, CreateAccountHeader, CreateAccountRequest, RetrievedUserDetails}
import uk.gov.hmrc.smartstub.{AdvGen, Enumerable}

import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}

object DataGenerators {

  def random[A](gen: Gen[A]): A = gen.sample.getOrElse(sys.error("Could not generate data"))

  val clientCode = Gen.identifier.map(_.take(5))

  val createAccountHeaderGen: Gen[CreateAccountHeader] =
    for {
      version    <- Gen.identifier
      date       <- Gen.choose(0L, 100L).map(t => ZonedDateTime.ofInstant(Instant.ofEpochSecond(t), ZoneId.of("UTC")))
      clientCode <- clientCode
      correlationId <- Gen.uuid
    } yield CreateAccountHeader(version, date, clientCode, correlationId)

  val contactDetailsGen: Gen[ContactDetails] =
    for {
      line1                   <- Gen.identifier
      line2                   <- Gen.identifier
      line3                   <- Gen.option(Gen.identifier)
      line4                   <- Gen.option(Gen.identifier)
      line5                   <- Gen.option(Gen.identifier)
      postcode                <- Gen.identifier
      countryCode             <- Gen.option(Gen.alphaStr)
      communicationPreference <- Gen.alphaStr
      phoneNumber             <- Gen.option(Gen.numStr)
      email                   <- Gen.option(Gen.alphaStr)
    } yield ContactDetails(
      line1,
      line2,
      line3,
      line4,
      line5,
      postcode,
      countryCode,
      communicationPreference,
      phoneNumber,
      email
    )

  val bankDetailsGen: Gen[BankDetails] =
    for {
      sortCode      <- Gen.numStr
      accountNumber <- Gen.numStr
      accountName   <- Gen.alphaStr
      rollNumber    <- Gen.option(Gen.identifier)
    } yield BankDetails(accountNumber, sortCode, accountName, rollNumber)

  val validCreateAccountBodyGen: Gen[CreateAccountBody] =
    for {
      nino                <- Gen.identifier
      name                <- Gen.alphaStr
      surname             <- Gen.alphaStr
      dob                 <- Gen.choose(1L, 100L).map(LocalDate.ofEpochDay)
      contactDetails      <- contactDetailsGen
      registrationChannel <- Gen.alphaStr
      bankDetails         <- Gen.option(bankDetailsGen)
      systemId            <- clientCode.map(code => "MDTP-API-" + code)
    } yield CreateAccountBody(nino, name, surname, dob, contactDetails, registrationChannel, bankDetails, systemId)

  val validCreateAccountRequestGen: Gen[CreateAccountRequest] =
    for {
      header <- createAccountHeaderGen
      body   <- validCreateAccountBodyGen
    } yield CreateAccountRequest(header, body)

  val addressGen: Gen[ItmpAddress] =
    for {
      line1       <- Gen.option(Gen.alphaStr)
      line2       <- Gen.option(Gen.alphaStr)
      line3       <- Gen.option(Gen.alphaStr)
      line4       <- Gen.option(Gen.alphaStr)
      line5       <- Gen.option(Gen.alphaStr)
      postCode    <- Gen.option(Gen.postcode)
      countryName <- Gen.option(Gen.alphaStr)
      countryCode <- Gen.option(Gen.alphaStr)
    } yield ItmpAddress(line1, line2, line3, line4, line5, postCode, countryName, countryCode)

  val retrievedUserDetailsGen: Gen[RetrievedUserDetails] =
    for {
      nino        <- Gen.option(Enumerable.instances.ninoEnum.gen)
      forename    <- Gen.option(Gen.forename())
      surname     <- Gen.option(Gen.surname)
      dateOfBirth <- Gen.option(Gen.date)
      address     <- Gen.option(addressGen)
      email       <- Gen.option(Gen.alphaStr)
    } yield RetrievedUserDetails(nino, forename, surname, dateOfBirth, address, email)

}
