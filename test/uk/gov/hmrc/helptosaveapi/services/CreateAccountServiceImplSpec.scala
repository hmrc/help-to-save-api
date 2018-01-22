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

package uk.gov.hmrc.helptosaveapi.services

import org.scalacheck.Arbitrary
import org.scalamock.handlers.CallHandler3
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.JsString
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveapi.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosaveapi.models.CreateAccountBody
import uk.gov.hmrc.helptosaveapi.util.{DataGenerators, TestSupport, toFuture}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class CreateAccountServiceImplSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  val htsConnector: HelpToSaveConnector = mock[HelpToSaveConnector]

  val service: CreateAccountServiceImpl = new CreateAccountServiceImpl(htsConnector)

  def mockHtsConnector(expectedBody: CreateAccountBody)(response: HttpResponse): CallHandler3[CreateAccountBody, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (htsConnector.createAccount(_: CreateAccountBody)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedBody, *, *)
      .returning(response)

  "The CreateAccountServiceImpl" must {

    "always return the response as is from the connector" in {
      implicit val createAccountBodyArb: Arbitrary[CreateAccountBody] = Arbitrary(DataGenerators.createAccountBodyGen)

      forAll{ (body: CreateAccountBody, status: Int, response: String) ⇒
        mockHtsConnector(body)(HttpResponse(status, Some(JsString(response))))
        val result = await(service.createAccount(body))
        result.status shouldBe status
        result.json shouldBe JsString(response)
      }
    }

  }

}
