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
import uk.gov.hmrc.helptosaveapi.models.{AccountAlreadyExists, ApiEligibilityResponse, Eligibility}
import uk.gov.hmrc.helptosaveapi.repo.EligibilityStore.EligibilityResponseWithNINO
import uk.gov.hmrc.helptosaveapi.util.TestSupport

class EligibilityStoreSpec extends TestSupport with MongoSupport {

  val conf = Configuration(
    ConfigFactory.parseString(
      """
        | mongo-cache.expireAfter =  2 seconds
      """.stripMargin)
  )

  "The EligibilityStoreSpec" when {

    val store = new MongoEligibilityStore(conf, reactiveMongoComponent)

    val cId = UUID.randomUUID()
    val nino = "nino"
    val eligibility = ApiEligibilityResponse(Eligibility(true, true, true), false)
    val eligibilityWithNINO = EligibilityResponseWithNINO(eligibility, nino)

    "storing api eligibility" must {

      "store the eligibility result and return success result" in {
        await(store.put(UUID.randomUUID(), eligibility, nino)) shouldBe Right(())
      }

      "store the AccountAlreadyExists result and return success result" in {
        await(store.put(UUID.randomUUID(), AccountAlreadyExists(), nino)) shouldBe Right(())
      }

      "handle unexpected future failures" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val store = new MongoEligibilityStore(conf, reactiveMongoComponent)
          await(store.put(UUID.randomUUID(), eligibility, nino)) shouldBe Left("error")
        }
      }
    }

    "getting api eligibility" must {

      "get the eligibility result and return success result" in {
        await(store.put(cId, eligibility, nino))
        await(store.get(cId)) shouldBe Right(Some(eligibilityWithNINO))
      }

      "be able to read the  AccountAlreadyExists result and return success result" in {
        await(store.put(cId, AccountAlreadyExists(), nino)) shouldBe Right(())
        await(store.get(cId)) shouldBe Right(Some(EligibilityResponseWithNINO(AccountAlreadyExists(), nino)))
      }

      "handle unexpected future failures" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val store = new MongoEligibilityStore(conf, reactiveMongoComponent)
          await(store.get(cId)) shouldBe Left("error")
        }
      }
    }
  }
}
