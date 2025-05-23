/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.{Configuration, Logger}

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

@ImplementedBy(classOf[LogMessageTransformerImpl])
trait LogMessageTransformer {
  def transform(message: String, nino: String, additionalParams: Seq[(String, String)]): String
}

@Singleton
class LogMessageTransformerImpl @Inject() (configuration: Configuration) extends LogMessageTransformer {

  private val ninoPrefix: String => String =
    if (configuration.underlying.getBoolean("nino-logging.enabled")) { nino =>
      s"For NINO [$nino], "
    } else { _ =>
      ""
    }

  private val logAdditionalParams: Seq[(String, String)] => String = { params =>
    params.toList.foldLeft("") { (acc, tuple) =>
      s"${acc}For ${tuple._1} [${tuple._2}], "
    }
  }

  def transform(message: String, nino: String, additionalParams: Seq[(String, String)]): String =
    ninoPrefix(nino) + logAdditionalParams(additionalParams) + message

}
