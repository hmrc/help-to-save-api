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

package uk.gov.hmrc.helptosaveapi.logging

import play.api.Logger
import uk.gov.hmrc.helptosaveapi.util.LogMessageTransformer

trait Logging {

  val logger: Logger = Logger(this.getClass)

}

object Logging {

  implicit class LoggerOps(val logger: Logger) {

    def info(message: String, nino: String, additionalParams: (String, String)*)(implicit
      transformer: LogMessageTransformer
    ): Unit =
      logger.info(transformer.transform(message, nino, additionalParams))

    def warn(message: String, nino: String, additionalParams: (String, String)*)(implicit
      transformer: LogMessageTransformer
    ): Unit =
      logger.warn(transformer.transform(message, nino, additionalParams))
  }

}
