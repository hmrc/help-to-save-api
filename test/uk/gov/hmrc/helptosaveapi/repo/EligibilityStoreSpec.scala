/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.LocalTime
import java.util.UUID

import uk.gov.hmrc.helptosaveapi.models.{AccountAlreadyExists, ApiEligibilityResponse, Eligibility}
import uk.gov.hmrc.helptosaveapi.repo.EligibilityStore.EligibilityResponseWithNINO
import uk.gov.hmrc.helptosaveapi.util.{Logging, TestSupport}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig


class EligibilityStoreSpec extends TestSupport with MongoTestSupport with Logging {

  val mockMongoComponent: MongoComponent = fakeApplication.injector.instanceOf[MongoComponent]
  val mockServicesConfig: ServicesConfig = fakeApplication.injector.instanceOf[ServicesConfig]

  class TestProps {
    val store = new MongoEligibilityStore(mockMongoComponent, mockServicesConfig)
  }

  "The EligibilityStoreSpec" when {

    val nino = "nino"
    val eligibility = ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), accountExists = false)
    val eligibilityWithNINO = EligibilityResponseWithNINO(eligibility, nino)

    "storing api eligibility" must {

      "store the eligibility result and return success result" in new TestProps {
        await(store.put(UUID.randomUUID(), eligibility, nino)) shouldBe Right(())
      }

      "store the AccountAlreadyExists result and return success result" in new TestProps {
        await(store.put(UUID.randomUUID(), AccountAlreadyExists(), nino)) shouldBe Right(())
      }
    }

    "getting api eligibility" must {

      "be able to read the  AccountAlreadyExists result and return success result" in new TestProps {
        val cId: UUID = UUID.randomUUID()
        await(store.put(cId, AccountAlreadyExists(), nino)) shouldBe Right(())
        await(store.get(cId)) shouldBe Right(Some(EligibilityResponseWithNINO(AccountAlreadyExists(), nino)))
      }

      "get the eligibility result and return success result" in new TestProps {
        val cId: UUID = UUID.randomUUID()
                                  val response: Either[String, Unit] = await(store.put(cId, eligibility, nino))
                                  logger.info(s"Eligibility result response: ${response.toString} time: ${LocalTime.now()}")
        response shouldBe Right(())
        await(store.get(cId)) shouldBe Right(Some(eligibilityWithNINO))
      }

    }
  }
}
