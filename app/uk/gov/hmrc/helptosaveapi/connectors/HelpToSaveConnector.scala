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
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def createAccount(body: CreateAccountBody, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (config: Configuration, http: WSHttp) extends HelpToSaveConnector {

  val createAccountUrl: String = {
    val host = config.underlying.getString("microservice.services.help-to-save.host")
    val port = config.underlying.getInt("microservice.services.help-to-save.port")

    s"http://$host:$port/help-to-save/create-de-account"
  }

  override def createAccount(body: CreateAccountBody, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    http.post(createAccountUrl, body, Map("X-CorrelationId" -> correlationId.toString))
  }

}
