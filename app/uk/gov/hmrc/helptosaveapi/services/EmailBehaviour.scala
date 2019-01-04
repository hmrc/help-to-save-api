/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.UUID

import play.mvc.Http.Status
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.models.{ApiBackendError, ApiError}
import uk.gov.hmrc.helptosaveapi.util.Logging._
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, PagerDutyAlerting, base64Encode}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

private[services] trait EmailBehaviour {
  this: HelpToSaveApiService with Logging ⇒

  val helpToSaveConnector: HelpToSaveConnector
  val pagerDutyAlerting: PagerDutyAlerting

  type StoreEmailResponseType = Future[Either[ApiError, Unit]]

  def storeEmail(nino:          String,
                 email:         String,
                 correlationId: UUID)(implicit hc: HeaderCarrier,
                                      ec:                    ExecutionContext,
                                      logMessageTransformer: LogMessageTransformer): StoreEmailResponseType = {

    val correlationIdHeader = "requestCorrelationId" -> correlationId.toString
    helpToSaveConnector.storeEmail(base64Encode(email), nino, correlationId).map[Either[ApiError, Unit]] {
      response ⇒
        response.status match {
          case Status.OK ⇒
            logger.info("successfully stored email for the api user", nino, correlationIdHeader)
            Right(())
          case other: Int ⇒
            logger.warn(s"could not store email in mongo for the api user, status: $other", nino, correlationIdHeader)
            pagerDutyAlerting.alert("unexpected status during storing email for the api user")
            Left(ApiBackendError())
        }
    }.recover {
      case e ⇒
        logger.warn(s"error during storing email for the api user, error=${e.getMessage}")
        pagerDutyAlerting.alert("could not store email in mongo for the api user")
        Left(ApiBackendError())
    }
  }

}
