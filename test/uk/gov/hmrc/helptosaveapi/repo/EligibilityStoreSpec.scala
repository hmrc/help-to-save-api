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

import java.util.UUID

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import reactivemongo.core.commands.LastError
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.helptosaveapi.models.Eligibility
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import uk.gov.hmrc.mongo.{DatabaseUpdate, Saved}

class EligibilityStoreSpec extends TestSupport with MongoTestSupport[Eligibility, EligibilityStore] {

  val conf = Configuration(
    ConfigFactory.parseString(
      """
        | mongo-cache.expireAfter =  2 seconds
      """.stripMargin)
  )

  override def newMongoStore() = new MongoEligibilityStore(conf, mockMongo) {

    override private[repo] def doCreateOrUpdate(id: Id, key: String, toCache: JsValue) =
      mockDBFunctions.doCreateOrUpdate(id, key, toCache)

    override private[repo] def doFindById(id: Id) =
      mockDBFunctions.doFindById(id)
  }

  "The EligibilityStoreSpec" when {

    val cId = UUID.randomUUID()
    val eligibility = Eligibility(true, true, true)

    "storing api eligibility" must {

      "store the eligibility result and return success result" in {

        mockDoCreateOrUpdate(Id(cId.toString), "eligibility", Json.toJson(eligibility))(Right(DatabaseUpdate[Cache](LastError(true, None, None, None, None, 1, false), Saved[Cache](Cache(Id(cId.toString))))))
        await(newMongoStore().put(cId, eligibility)) shouldBe Right(())
      }

      "handle unexpected future failures" in {
        mockDoCreateOrUpdate(Id(cId.toString), "eligibility", Json.toJson(eligibility))(Left("error"))
        await(newMongoStore().put(cId, eligibility)) shouldBe Left("error")
      }
    }

    "getting api eligibility" must {

      "get the eligibility result and return success result" in {
        val json =
          s"""{
             | "eligibility":${Json.toJson(eligibility)}
             }""".stripMargin
        mockDoFindById(Id(cId.toString))(Right(Some(Cache(Id(cId.toString), Some(Json.parse(json))))))
        await(newMongoStore().get(cId)) shouldBe Right(Some(eligibility))
      }

      "handle unexpected future failures" in {
        mockDoFindById(Id(cId.toString))(Left("error"))
        await(newMongoStore().get(cId)) shouldBe Left("error")
      }
    }
  }
}
