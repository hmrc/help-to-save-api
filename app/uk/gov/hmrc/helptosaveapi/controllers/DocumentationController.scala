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

package uk.gov.hmrc.helptosaveapi.controllers

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import controllers.AssetsBuilder
import play.api.Configuration
import play.api.http.HttpErrorHandler
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosaveapi.views.txt
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import configs.syntax._
import uk.gov.hmrc.helptosaveapi.controllers.DocumentationController.APIAccess
import uk.gov.hmrc.helptosaveapi.controllers.DocumentationController.APIAccess.Version
import uk.gov.hmrc.helptosaveapi.util.WithMdcExecutionContext
import uk.gov.hmrc.play.bootstrap.controller.ActionWithMdc

@Singleton
class DocumentationController @Inject() (httpErrorHandler: HttpErrorHandler, configuration: Configuration)
  extends AssetsBuilder(httpErrorHandler) with BaseController with WithMdcExecutionContext {

  val access: Version ⇒ APIAccess = APIAccess(configuration.underlying.getConfig("api.access"))

  val versionEnabled: Version ⇒ Boolean = version ⇒ configuration.underlying.getBoolean(s"api.access.version-$version.enabled")

  def definition(): Action[AnyContent] = ActionWithMdc {
    Ok(txt.definition(access, versionEnabled)).as("application/json")
  }

  def raml(version: String, file: String): Action[AnyContent] =
    at(s"/public/api/conf/$version", file)

}

object DocumentationController {

  case class APIAccess(`type`: String, whitelistedApplicationIds: List[String])

  object APIAccess {

    implicit val apiAccessFormats: Format[APIAccess] = Json.format[APIAccess]

    type Version = String

    def apply(config: Config)(version: Version): APIAccess =
      APIAccess(
        `type`                    = config.getString(s"version-$version.type"),
        whitelistedApplicationIds = config.get[List[String]](s"version-$version.whitelistedApplicationIds").value
      )
  }

}
