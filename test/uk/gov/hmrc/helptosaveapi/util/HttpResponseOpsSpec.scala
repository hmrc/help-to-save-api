/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.util
import org.mockito.Mockito.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json, Reads}
import uk.gov.hmrc.http.HttpResponse

class HttpResponseOpsSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  implicit val mapReads: Reads[Map[String, String]] = Reads.mapReads[String]

  "HttpResponseOps" should "parse valid JSON successfully" in {
    val validJson = Json.parse("""{"key": "value"}""")
    val httpResponse = mock[HttpResponse]
    when(httpResponse.json).thenReturn(validJson)
    when(httpResponse.body).thenReturn("""{"key": "value"}""")

    val ops = new HttpResponseOps(httpResponse)
    ops.parseJson[Map[String, String]] shouldBe Right(Map("key" -> "value"))
  }

  it should "return an error if the response does not contain JSON" in {
    val httpResponse = mock[HttpResponse]
    when(httpResponse.json).thenThrow(new RuntimeException("Invalid JSON"))
    when(httpResponse.body).thenReturn("Not a JSON")

    val ops = new HttpResponseOps(httpResponse)
    ops.parseJson[Map[String, String]] shouldBe Left(
      "Could not read http response as JSON (Invalid JSON). Response body was Not a JSON"
    )
  }

  it should "return an error if the JSON is invalid" in {
    val invalidJson = Json.parse("""{"key": 123}""")
    val httpResponse = mock[HttpResponse]
    when(httpResponse.json).thenReturn(invalidJson)
    when(httpResponse.body).thenReturn("""{"key": 123}""")

    val ops = new HttpResponseOps(httpResponse)
    ops.parseJson[Map[String, String]] shouldBe Left(
      "Could not parse http reponse JSON: /key: [error.expected.jsstring]. Response body was {\"key\": 123}"
    )
  }

  it should "return an error if the JSON is null" in {
    val httpResponse = mock[HttpResponse]
    when(httpResponse.json).thenReturn(null.asInstanceOf[JsValue])
    when(httpResponse.body).thenReturn("null")

    val ops = new HttpResponseOps(httpResponse)
    ops.parseJson[Map[String, String]] shouldBe Left("No JSON found in body of http response")
  }
}
