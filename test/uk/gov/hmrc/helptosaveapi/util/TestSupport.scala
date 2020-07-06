/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.Materializer
import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import com.typesafe.config.ConfigFactory
import controllers.Assets
import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import play.api.http.HttpErrorHandler
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.helptosaveapi.metrics.Metrics
import uk.gov.hmrc.helptosaveapi.validators.EmailValidation
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class TestSupport extends UnitSpec with MockFactory {

  lazy val fakeApplication =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString("""
                                      | metrics.enabled       = false
                                      | play.modules.disabled = [ "uk.gov.hmrc.helptosaveapi.RegistrationModule",
                                      | "play.modules.reactivemongo.ReactiveMongoHmrcModule",
                                      | "play.api.mvc.LegacyCookiesModule" ]
          """.stripMargin)
        )
      )
      .build()

  implicit lazy val materializer: Materializer = fakeApplication.materializer

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  lazy val mockCc: ControllerComponents = fakeApplication.injector.instanceOf[ControllerComponents]

  lazy val mockAssets: Assets = fakeApplication.injector.instanceOf[Assets]

  lazy val appName: String = "fakeApp"

  implicit lazy val config: Configuration = fakeApplication.injector.instanceOf[Configuration]

  val httpErrorHandler: HttpErrorHandler = mock[HttpErrorHandler]

  val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

  implicit lazy val logMessageTransformer: LogMessageTransformer =
    fakeApplication.injector.instanceOf[LogMessageTransformer]

  implicit val mockEmailValidation: EmailValidation =
    new EmailValidation(
      Configuration(
        "email-validation.max-total-length" → Int.MaxValue,
        "email-validation.max-local-length" → Int.MaxValue,
        "email-validation.max-domain-length" → Int.MaxValue
      )
    )
}
