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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.helptosaveapi.models.Eligibility

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEligibilityStore])
trait EligibilityStore {

  def get(correlationId: UUID)(implicit ec: ExecutionContext): Future[Either[String, Option[Eligibility]]]

  def put(correlationId: UUID, eligibility: Eligibility)(implicit ec: ExecutionContext): Future[Either[String, Unit]]

}

@Singleton
class MongoEligibilityStore @Inject() (config: Config,
                                       mongo:  ReactiveMongoComponent)(implicit ec: ExecutionContext) extends EligibilityStore {

  private val expireAfterSeconds = config.getDuration("mongo-cache.expireAfter").getSeconds

  private val cacheRepository = new CacheMongoRepository("api-eligibility", expireAfterSeconds)(mongo.mongoConnector.db, ec)

  override def get(correlationId: UUID)(implicit ec: ExecutionContext): Future[Either[String, Option[Eligibility]]] = {
    cacheRepository.findById(Id(correlationId.toString)).map { maybeCache ⇒
      Right(maybeCache.flatMap(_.data.map(value ⇒ (value \ "eligibility").as[Eligibility])))
    }.recover {
      case e ⇒
        Left(e.getMessage)
    }
  }

  override def put(correlationId: UUID, eligibility: Eligibility)(implicit ec: ExecutionContext): Future[Either[String, Unit]] = {
    cacheRepository.createOrUpdate(Id(correlationId.toString), "eligibility", Json.toJson(eligibility)).map[Either[String, Unit]] {
      dbUpdate ⇒
        if (dbUpdate.writeResult.inError) {
          Left(dbUpdate.writeResult.errMsg.getOrElse("unknown error during inserting eligibility document"))
        } else {
          Right(())
        }
    }.recover {
      case e ⇒
        Left(e.getMessage)
    }
  }
}