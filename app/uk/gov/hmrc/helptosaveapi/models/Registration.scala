package uk.gov.hmrc.helptosaveapi.models

import play.api.libs.json.Json

case class Registration(serviceName: String, serviceUrl: String, metadata: Option[Map[String, String]] = None)

object Registration {
  implicit val regFormat = Json.format[Registration]
}