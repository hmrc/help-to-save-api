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

package uk.gov.hmrc.helptosaveapi.repo

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.JsValue
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.DatabaseUpdate

import scala.concurrent.Future

trait MongoTestSupport[Data, Repo] {
  this: MockFactory ⇒

  trait MockDBFunctions {
    def doCreateOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]]

    def doFindById(id: Id): Future[Option[Cache]]
  }

  val mockDBFunctions = mock[MockDBFunctions]

  val mockMongo = mock[ReactiveMongoComponent]

  def newMongoStore(): Repo

  def mockDoCreateOrUpdate(id: Id, key: String, toCache: JsValue)(response: Either[String, DatabaseUpdate[Cache]]) =
    (mockDBFunctions.doCreateOrUpdate(_: Id, _: String, _: JsValue))
      .expects(id, key, toCache)
      .returning(response.fold[Future[DatabaseUpdate[Cache]]](
        error ⇒ Future.failed(new RuntimeException(error)),
        cache ⇒ Future.successful(cache)
      ))

  def mockDoFindById(id: Id)(response: Either[String, Option[Cache]]) =
    (mockDBFunctions.doFindById(_: Id))
      .expects(id)
      .returning(response.fold[Future[Option[Cache]]](
        error ⇒ Future.failed(new RuntimeException(error)),
        cache ⇒ Future.successful(cache)
      ))
}
