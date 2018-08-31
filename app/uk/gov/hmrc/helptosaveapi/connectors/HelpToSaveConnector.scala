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

package uk.gov.hmrc.helptosaveapi.connectors

import java.util.UUID

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnectorImpl.CreateAccountInfo
import uk.gov.hmrc.helptosaveapi.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosaveapi.models.createaccount.CreateAccountBody
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String, eligibilityReason: Int)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def checkEligibility(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def getAccount(nino: String, systemId: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def storeEmail(encodedEmail: String, nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (config: Configuration,
                                         http:   HttpClient)(implicit transformer: LogMessageTransformer)
  extends HelpToSaveConnector with Logging {

  private val htsBaseUrl = {
    val host = config.underlying.getString("microservice.services.help-to-save.host")
    val port = config.underlying.getInt("microservice.services.help-to-save.port")
    s"http://$host:$port/help-to-save"
  }

  val createAccountUrl: String = s"$htsBaseUrl/create-account"

  private val storeEmailURL = s"$htsBaseUrl/store-email"

  def eligibilityCheckUrl(nino: String): String = s"$htsBaseUrl/api/eligibility-check/$nino"

  def getAccountUrl(nino: String): String = s"$htsBaseUrl/$nino/account"

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  override def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String, eligibilityReason: Int)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(createAccountUrl, CreateAccountInfo(body, eligibilityReason, clientCode), Map(correlationIdHeaderName -> correlationId.toString))

  override def checkEligibility(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(eligibilityCheckUrl(nino), emptyQueryParameters, Map(correlationIdHeaderName -> correlationId.toString))

  override def getAccount(nino: String, systemId: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(getAccountUrl(nino), Map("systemId" -> systemId, "correlationId" -> correlationId.toString), Map(correlationIdHeaderName -> correlationId.toString)
    )

  override def storeEmail(encodedEmail: String, nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(storeEmailURL, Map("email" -> encodedEmail, "nino" -> nino), Map(correlationIdHeaderName -> correlationId.toString))
}

object HelpToSaveConnectorImpl {

  case class CreateAccountInfo(userInfo: CreateAccountBody, eligibilityReason: Int, source: String)

  implicit val createAccountInfoFormat: Format[CreateAccountInfo] = Json.format[CreateAccountInfo]
}
