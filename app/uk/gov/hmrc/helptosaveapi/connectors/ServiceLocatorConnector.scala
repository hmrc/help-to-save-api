package uk.gov.hmrc.helptosaveapi.connectors

import javax.inject.Inject

import play.api.Logger
import com.google.inject.{ImplementedBy, Singleton}
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.helptosaveapi.http.WSHttp
import uk.gov.hmrc.helptosaveapi.models.Registration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future

@ImplementedBy(classOf[ServiceLocatorConnectorImpl])
trait ServiceLocatorConnector extends ServicesConfig {

  def register(): Future[Boolean]
}

@Singleton
class ServiceLocatorConnectorImpl @Inject()(config: Configuration, http: WSHttp) extends ServiceLocatorConnector {

  private lazy val appName: String = getString("appName")
  private lazy val appUrl: String = getString("appUrl")
  private lazy val serviceUrl: String = baseUrl("service-locator")

  val metadata: Option[Map[String, String]] = Some(Map("third-party-api" -> "true"))

  val handlerOK: () => Unit = () => Logger.info("Service is registered on the service locator")
  val handlerError: Throwable => Unit = e => Logger.error("Service could not register on the service locator", e)

  def register(): Future[Boolean] = {
    implicit val hc: HeaderCarrier = new HeaderCarrier

    val registration = Registration(appName, appUrl, metadata)
    http.POST(s"$serviceUrl/registration", registration, Seq(HeaderNames.CONTENT_TYPE -> ContentTypes.JSON)) map {
      _ =>
        handlerOK()
        true
    } recover {
      case e: Throwable =>
        handlerError(e)
        false
    }
  }
}