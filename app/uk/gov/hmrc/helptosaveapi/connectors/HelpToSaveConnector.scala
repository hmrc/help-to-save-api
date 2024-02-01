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

package uk.gov.hmrc.helptosaveapi.connectors

import java.util.UUID

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnectorImpl.CreateAccountInfo
import uk.gov.hmrc.helptosaveapi.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody
import uk.gov.hmrc.helptosaveapi.util.Logging
import uk.gov.hmrc.helptosaveapi.models.ValidateBankDetailsRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String, eligibilityReason: Int)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse]

  def checkEligibility(nino: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse]

  def getAccount(nino: String, systemId: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse]

  def storeEmail(encodedEmail: String, nino: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse]

  def validateBankDetails(
    request: ValidateBankDetailsRequest
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def getUserEnrolmentStatus(nino: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse]
}

@Singleton
class HelpToSaveConnectorImpl @Inject() (config: Configuration, http: HttpClient)()
    extends HelpToSaveConnector with Logging {

  private val htsBaseUrl = {
    val protocol = config.underlying.getString("microservice.services.help-to-save.protocol")
    val host = config.underlying.getString("microservice.services.help-to-save.host")
    val port = config.underlying.getInt("microservice.services.help-to-save.port")
    s"$protocol://$host:$port/help-to-save"
  }

  val createAccountUrl: String = s"$htsBaseUrl/create-account"

  private val storeEmailURL = s"$htsBaseUrl/store-email"

  private val getEnrolmentStatusURL = s"$htsBaseUrl/enrolment-status"

  val eligibilityCheckUrl: String = s"$htsBaseUrl/eligibility-check"

  def getAccountUrl(nino: String): String = s"$htsBaseUrl/$nino/account"

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  override def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String, eligibilityReason: Int)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    http.post(
      createAccountUrl,
      CreateAccountInfo(body, eligibilityReason, clientCode),
      Map(correlationIdHeaderName -> correlationId.toString)
    )

  override def checkEligibility(
    nino: String,
    correlationId: UUID
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(eligibilityCheckUrl, Map("nino" -> nino), Map(correlationIdHeaderName -> correlationId.toString))

  override def getAccount(nino: String, systemId: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    http.get(
      getAccountUrl(nino),
      Map("systemId"              -> systemId, "correlationId" -> correlationId.toString),
      Map(correlationIdHeaderName -> correlationId.toString)
    )

  override def storeEmail(encodedEmail: String, nino: String, correlationId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    http.get(
      storeEmailURL,
      Map("email"                 -> encodedEmail, "nino" -> nino),
      Map(correlationIdHeaderName -> correlationId.toString)
    )

  override def validateBankDetails(
    request: ValidateBankDetailsRequest
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse] =
    http.post(s"$htsBaseUrl/validate-bank-details", request)

  override def getUserEnrolmentStatus(
    nino: String,
    correlationId: UUID
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(getEnrolmentStatusURL, Map("nino" -> nino), Map(correlationIdHeaderName -> correlationId.toString))
}

object HelpToSaveConnectorImpl {

  case class CreateAccountInfo(payload: CreateAccountBody, eligibilityReason: Int, source: String)

  implicit val createAccountInfoWrites: Writes[CreateAccountInfo] = Json.writes[CreateAccountInfo]
}
