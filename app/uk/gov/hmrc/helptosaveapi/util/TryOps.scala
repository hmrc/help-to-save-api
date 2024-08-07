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

package uk.gov.hmrc.helptosaveapi.util

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object TryOps {
  implicit def foldOps[A](t: Try[A]): TryOps[A] = new TryOps[A](t)
}

class TryOps[A](val t: Try[A]) extends AnyVal {

  def fold[B](f: Throwable => B, g: A => B): B = t match {
    case Failure(e) => f(e)
    case Success(s) => g(s)
  }

}
