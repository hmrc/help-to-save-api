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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.{should, shouldBe}

import scala.util.{Failure, Success, Try}

class TryOpsSpec extends AnyFlatSpec with Matchers {

  "fold method" should "return the result of the success function when Try is Success" in {
    val successTry: Try[Int] = Success(42)
    val result = new TryOps(successTry).fold(_ => "failure", s => s.toString)
    result shouldBe "42"
  }

  it should "return the result of the failure function when Try is Failure" in {
    val failureTry = Failure(new RuntimeException("error"))
    val tryOps = new TryOps(failureTry)
    val result = tryOps.fold(e => e.getMessage, _ => "success")
    result shouldBe "error"
  }

  it should "handle different types correctly for Success" in {
    val successTry = Success("hello")
    val tryOps = new TryOps(successTry)
    val result = tryOps.fold(_ => "failure", s => s.toUpperCase)
    result shouldBe "HELLO"
  }

  it should "handle different types correctly for Failure" in {
    val failureTry = Failure(new IllegalArgumentException("invalid argument"))
    val tryOps = new TryOps(failureTry)
    val result = tryOps.fold(e => e.getMessage, _ => "success")
    result shouldBe "invalid argument"
  }

  it should "handle nested Try values correctly for Success" in {
    val nestedSuccessTry = TryOps(Success(TryOps(Success(42))))
    val result = nestedSuccessTry.fold(
      _ => "Outer Error",
      innerTry =>
        innerTry.fold(
          _ => "Inner Error",
          value => s"Inner Success: $value"
        )
    )
    result shouldBe "Inner Success: 42"
  }

  it should "handle nested Try values correctly for Failure" in {
    val nestedFailureTry: Try[Try[Int]] = Success(Failure(new RuntimeException("Inner failure")))
    val result = nestedFailureTry.fold(
      _ => "Outer Error",
      innerTry =>
        innerTry.fold(
          error => s"Inner Error: ${error.getMessage}",
          _ => "Inner Success"
        )
    )
    result shouldBe "Inner Error: Inner failure"
  }

  it should "implicitly convert Try to TryOps and use fold method" in {
    val successTry: Try[Int] = Success(42)
    val result = successTry.fold(_ => "failure", s => s.toString)
    result shouldBe "42"

    val failureTry = Failure(new RuntimeException("error"))
    val result2 = failureTry.fold(e => e.getMessage, _ => "success")
    result2 shouldBe "error"
  }
}
