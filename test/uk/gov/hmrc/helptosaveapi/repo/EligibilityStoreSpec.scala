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

package uk.gov.hmrc.helptosaveapi.repo

import org.scalatest.matchers.should.Matchers.shouldBe
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.helptosaveapi.logging.Logging
import uk.gov.hmrc.helptosaveapi.models.{AccountAlreadyExists, ApiEligibilityResponse, Eligibility, EligibilityResponseWithNINO}
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalTime}
import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

class EligibilityStoreSpec extends TestSupport with MongoTestSupport with Logging {

  val mockMongoComponent: MongoComponent = fakeApplication.injector.instanceOf[MongoComponent]
  val mockServicesConfig: ServicesConfig = fakeApplication.injector.instanceOf[ServicesConfig]

  class TestProps {
    val store = new MongoEligibilityStore(mockMongoComponent, mockServicesConfig)
  }

  class FailingMongoCacheRepo extends MongoCacheRepository(
        mockMongoComponent,
        "failing-eligibility-store",
        ttl = 60.seconds,
        timestampSupport = new CurrentTimestampSupport,
        cacheIdType = CacheIdType.SimpleCacheId
      ) {

    override def findById(id: String): Future[Option[CacheItem]] =
      Future.failed(new RuntimeException("Failed to find item in MongoDB"))

    override def put[A: Writes](
      id: String
    )(dataKey: DataKey[A], data: A): Future[CacheItem] =
      Future.failed(new RuntimeException("Failed to find item in MongoDB"))
  }

  class EligibilityCacheRepo extends MongoCacheRepository(
        mockMongoComponent,
        "eligibilityWithNINO-store",
        ttl = 60.seconds,
        timestampSupport = new CurrentTimestampSupport,
        cacheIdType = CacheIdType.SimpleCacheId
      ) {
    val deformedEligibilityWithNINOJsonInvalid: JsValue = Json.parse("""
                                                                       |{
                                                                       |     "eligibilit" : {
                                                                       |         "isEligible" : true,
                                                                       |         "hasWTC" : true,
                                                                       |         "hasUC" : true
                                                                       |        },
                                                                       |      "accountExists" : false
                                                                       |}
                                                                       |""".stripMargin)

    override def findById(id: String): Future[Option[CacheItem]] =
      Future.successful(
        Option(CacheItem("id", deformedEligibilityWithNINOJsonInvalid.as[JsObject], Instant.now(), Instant.now()))
      )

  }

  "The EligibilityStoreSpec" when {

    val nino = "nino"
    val eligibility =
      ApiEligibilityResponse(Eligibility(isEligible = true, hasWTC = true, hasUC = true), accountExists = false)
    val eligibilityWithNINO = EligibilityResponseWithNINO(eligibility, nino)
    val cId: UUID = UUID.randomUUID()

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
        await(store.put(cId, AccountAlreadyExists(), nino)) shouldBe Right(())
        await(store.get(cId)) shouldBe Right(Some(EligibilityResponseWithNINO(AccountAlreadyExists(), nino)))
      }

      "get the eligibility result and return success result" in new TestProps {
        val response: Either[String, Unit] = await(store.put(cId, eligibility, nino))
        logger.info(s"Eligibility result response: ${response.toString} time: ${LocalTime.now()}")
        response shouldBe Right(())
        await(store.get(cId)) shouldBe Right(Some(eligibilityWithNINO))
      }

    }

    "mongoRepo get call throws exception" should {
      "fail" in {
        val store: MongoEligibilityStore =
          new MongoEligibilityStore(mockMongoComponent, mockServicesConfig)(using ExecutionContext.global) {
            override val mongoRepo: FailingMongoCacheRepo = new FailingMongoCacheRepo()
          }
        val result = await(store.get(cId))
        result shouldBe Left("Failed to find item in MongoDB")
      }
    }

    "mongoRepo put call throws exception" should {
      "fail" in {
        val store: MongoEligibilityStore =
          new MongoEligibilityStore(mockMongoComponent, mockServicesConfig)(using ExecutionContext.global) {
            override val mongoRepo: FailingMongoCacheRepo = new FailingMongoCacheRepo()
          }
        val result = await(store.put(cId, eligibility, "nino"))
        result shouldBe Left("Failed to find item in MongoDB")
      }
    }

    "mongoRepo doFindById call throws exception for invalid response" in {
      val store: MongoEligibilityStore =
        new MongoEligibilityStore(mockMongoComponent, mockServicesConfig)(using ExecutionContext.global) {
          override val mongoRepo: EligibilityCacheRepo = new EligibilityCacheRepo()
        }
      await(store.doFindById(cId.toString))
      val response = await(store.get(cId))
      response contains Left("Could not parse data:")
    }
  }
}
