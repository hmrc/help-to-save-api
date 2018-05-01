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

import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.OK
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnectorImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.models._
import uk.gov.hmrc.helptosaveapi.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveapi.util.Logging.LoggerOps
import uk.gov.hmrc.helptosaveapi.util.{LogMessageTransformer, Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def createAccount(body: CreateAccountBody, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def checkEligibility(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, EligibilityResponse]]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (config:            Configuration,
                                         http:              WSHttp,
                                         metrics:           Metrics,
                                         pagerDutyAlerting: PagerDutyAlerting)(implicit transformer: LogMessageTransformer)
  extends HelpToSaveConnector with Logging {

  private val htsBBaseUrl = {
    val host = config.underlying.getString("microservice.services.help-to-save.host")
    val port = config.underlying.getInt("microservice.services.help-to-save.port")
    s"http://$host:$port/help-to-save"
  }

  val createAccountUrl: String = s"$htsBBaseUrl/create-de-account"

  def eligibilityCheckUrl(nino: String): String = s"$htsBBaseUrl/api/check-eligibility/$nino"

  val correlationIdHeaderName: String = config.underlying.getString("microservice.correlationIdHeaderName")

  override def createAccount(body: CreateAccountBody, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    http.post(createAccountUrl, body, Map(correlationIdHeaderName -> correlationId.toString))
  }

  override def checkEligibility(nino:          String,
                                correlationId: UUID)(implicit hc: HeaderCarrier,
                                                     ec: ExecutionContext): Future[Either[String, EligibilityResponse]] = {
    val timerContext = metrics.apiEligibilityCallTimer.time()
    http.get(eligibilityCheckUrl(nino), Map(correlationIdHeaderName -> correlationId.toString))
      .map {
        response ⇒
          val time = timerContext.stop()

          response.status match {
            case OK ⇒
              val result = {
                response.parseJson[EligibilityCheckResponse].flatMap(toApiEligibility)
              }
              result.fold({
                e ⇒
                  metrics.apiEligibilityCallErrorCounter.inc()
                  logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e", nino)
                  pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
              }, _ ⇒
                logger.info(s"Call to check eligibility successful, received 200 (OK)", nino)
              )
              result

            case other: Int ⇒
              logger.warn(s"Call to check eligibility unsuccessful. Received unexpected status $other", nino)
              metrics.apiEligibilityCallErrorCounter.inc()
              pagerDutyAlerting.alert("Received unexpected http status in response to eligibility check")
              Left(s"Received unexpected status $other")

          }
      }.recover {
        case e ⇒
          val time = timerContext.stop()
          pagerDutyAlerting.alert("Failed to make call to check eligibility")
          metrics.apiEligibilityCallErrorCounter.inc()
          Left(s"Call to check eligibility unsuccessful: ${e.getMessage}")
      }
  }

  // scalastyle:off magic.number
  private def toApiEligibility(r: EligibilityCheckResponse): Either[String, EligibilityResponse] =
    (r.resultCode, r.reasonCode) match {
      case (1, 6) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = false, hasUC = true), accountExists = false))
      case (1, 7) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = false), accountExists = false))
      case (1, 8) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), accountExists = false))

      case (2, 3) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = false), accountExists = false))
      case (2, 4) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = true, hasUC = true), accountExists = false))
      case (2, 5) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = true), accountExists = false))
      case (2, 9) ⇒ Right(ApiEligibilityResponse(Eligibility(isEligible = false, hasWTC = false, hasUC = false), accountExists = false))

      case (3, _) ⇒ Right(AccountAlreadyExists())
      case _      ⇒ Left(s"Invalid combination for eligibility response. Response was '$r'")
    }
}

object HelpToSaveConnectorImpl {

  private[HelpToSaveConnectorImpl] case class EligibilityCheckResponse(result: String, resultCode: Int, reason: String, reasonCode: Int)

  private[HelpToSaveConnectorImpl] object EligibilityCheckResponse {
    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]
  }

}
