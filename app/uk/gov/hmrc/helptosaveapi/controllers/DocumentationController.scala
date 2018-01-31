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

package controllers

import domain.APIAccess
import play.api.http.{HttpErrorHandler, LazyHttpErrorHandler}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosaveapi.config.AppContext
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.helptosaveapi.views.txt

class DocumentationController(httpErrorHandler: HttpErrorHandler)
  extends AssetsBuilder(httpErrorHandler) with BaseController {

  def definition(): Action[AnyContent] = Action {
    Ok(txt.definition(APIAccess.build(AppContext.access))).withHeaders(CONTENT_TYPE -> JSON)
  }

  def raml(version: String, file: String): Action[AnyContent] = {
    super.at(s"/public/api/conf/$version", file)
  }
}

object Documentation extends DocumentationController(LazyHttpErrorHandler)
