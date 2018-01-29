package controllers

import config.AppContext
import domain.APIAccess
import play.api.http.{HttpErrorHandler, LazyHttpErrorHandler}
import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import views.txt

abstract class DocumentationController(httpErrorHandler: HttpErrorHandler, appContext: AppContext) extends AssetsBuilder(httpErrorHandler) with  BaseController {

  def definition = Action {
    Ok(txt.definition(APIAccess.build(appContext.access))).withHeaders(CONTENT_TYPE -> JSON)
  }

  def raml(version: String, file: String) = {
    super.at(s"/public/api/conf/$version", file)
  }
}

object Documentation extends DocumentationController(LazyHttpErrorHandler, AppContext)