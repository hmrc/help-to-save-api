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

package uk.gov.hmrc.helptosaveapi

import java.util.UUID

import com.typesafe.config.ConfigFactory
import uk.gov.hmrc.helptosaveapi.models.Eligibility
import uk.gov.hmrc.helptosaveapi.repo.MongoEligibilityStore
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import uk.gov.hmrc.mongo.MongoSpecSupport

class EligibilityStoreSpec extends TestSupport with MongoSpecSupport {

  val conf = ConfigFactory.parseString("mongo-cache.expireAfter = 2 seconds")

  val store = new MongoEligibilityStore(conf, reactiveMongoComponent)

  "The EligibilityStoreSpec" when {

    val cId = UUID.randomUUID()
    val eligibility = Eligibility(true, true, true)

    "storing eligibility" must {

      "store the eligibility result and return success result" in {
        await(store.put(cId, eligibility)) shouldBe Right(())
      }

      "get the eligibility result and return success result" in {
        await(store.get(cId)) shouldBe Right(Some(eligibility))
      }
    }
  }
}
