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
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def checkEligibility(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def getAccount(nino: String, systemId: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def storeEmail(email: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (config: Configuration,
                                         http:   WSHttp)(implicit transformer: LogMessageTransformer)
  extends HelpToSaveConnector with Logging {

  private val htsBaseUrl = {
    val host = config.underlying.getString("microservice.services.help-to-save.host")
    val port = config.underlying.getInt("microservice.services.help-to-save.port")
    s"http://$host:$port/help-to-save"
  }

  val createAccountUrl: String = s"$htsBaseUrl/create-account"

  private def storeEmailURL(encodedEmail: String) =
    s"$htsBaseUrl/store-email?email=$encodedEmail"

  def eligibilityCheckUrl(nino: String): String = s"$htsBaseUrl/api/eligibility-check/$nino"

  def getAccountUrl(nino: String, systemId: String, correlationId: String): String = s"$htsBaseUrl/$nino/account?systemId=$systemId&correlationId=$correlationId"

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  override def createAccount(body: CreateAccountBody, correlationId: UUID, clientCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(createAccountUrl, CreateAccountInfo(body, clientCode), Map(correlationIdHeaderName -> correlationId.toString))

  override def checkEligibility(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(eligibilityCheckUrl(nino), Map(correlationIdHeaderName -> correlationId.toString))

  override def getAccount(nino: String, systemId: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(getAccountUrl(nino, systemId, correlationId.toString), Map(correlationIdHeaderName -> correlationId.toString))

  override def storeEmail(email: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(storeEmailURL(email), Map(correlationIdHeaderName -> correlationId.toString))

}

object HelpToSaveConnectorImpl {

  case class CreateAccountInfo(userInfo: CreateAccountBody, source: String)

  implicit val createAccountInfoFormat: Format[CreateAccountInfo] = Json.format[CreateAccountInfo]
}
