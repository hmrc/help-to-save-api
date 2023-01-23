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

package uk.gov.hmrc.helptosaveapi.util

import cats.data.{NonEmptyList, Validated, ValidatedNel}

object Validation {

  type Validation[A] = ValidatedNel[String, A]

  def validationFromBoolean[T, E](t: T)(predicate: T ⇒ Boolean, error: T ⇒ E): ValidatedNel[E, T] =
    if (predicate(t)) {
      Validated.Valid(t)
    } else {
      Validated.Invalid(NonEmptyList.of(error(t)))
    }

}
