/*
 * Copyright 2025 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{Configuration, Logger}

class LoggingSpec extends PlaySpec with MockitoSugar {

  "Logging trait" should {
    "instantiate logger correctly" in {
      val logging = new Logging {}
      logging.logger mustBe a[Logger]
    }
  }

  "LogMessageTransformerImpl" should {
    "transform messages correctly when nino-logging is enabled" in {
      val config = Configuration("nino-logging.enabled" -> true)
      val transformer = new LogMessageTransformerImpl(config)
      val message = "Test message"
      val nino = "AA123456A"
      val additionalParams = Seq("param1" -> "value1", "param2" -> "value2")

      val transformedMessage = transformer.transform(message, nino, additionalParams)

      transformedMessage mustBe "For NINO [AA123456A], For param1 [value1], For param2 [value2], Test message"
    }

    "transform messages correctly when nino-logging is disabled" in {
      val config = Configuration("nino-logging.enabled" -> false)
      val transformer = new LogMessageTransformerImpl(config)
      val message = "Test message"
      val nino = "AA123456A"
      val additionalParams = Seq("param1" -> "value1", "param2" -> "value2")

      val transformedMessage = transformer.transform(message, nino, additionalParams)

      transformedMessage mustBe "For param1 [value1], For param2 [value2], Test message"
    }
  }
}
