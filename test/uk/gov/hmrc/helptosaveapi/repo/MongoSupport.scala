/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.FailoverStrategy
import reactivemongo.core.actors.Exceptions.PrimaryUnavailableException
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

trait MongoSupport extends MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll { this: Suite ⇒

  val reactiveMongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  def withBrokenMongo(f: ReactiveMongoComponent ⇒ Unit): Unit =
    scala.util.control.Exception.ignoring(classOf[PrimaryUnavailableException]) {
      val reactiveMongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
        override def mongoConnector: MongoConnector =
          MongoConnector(s"mongodb://127.0.0.1:27018/$databaseName", Some(FailoverStrategy(retries = 0)))
      }

      try {
        f(reactiveMongoComponent)
      } finally {
        reactiveMongoComponent.mongoConnector.helper.driver.close()
      }
    }

  abstract override def beforeEach(): Unit = {
    super.beforeEach()
    mongo().drop()
  }

  abstract override def afterAll(): Unit = {
    super.afterAll()
    reactiveMongoComponent.mongoConnector.helper.driver.close()
  }

}
