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

package uk.gov.hmrc.helptosaveapi.models

import cats.instances.string._
import cats.syntax.eq._
import uk.gov.hmrc.auth.core.retrieve.Credentials

sealed trait AccessType

object AccessType {

  case object PrivilegedAccess extends AccessType

  case object UserRestricted extends AccessType

  def fromCredentials(credentials: Credentials): Either[String, AccessType] = {
    val providerType = credentials.providerType
    if (providerType === "GovernmentGateway") {
      Right(UserRestricted)
    } else if (providerType === "PrivilegedApplication") {
      Right(PrivilegedAccess)
    } else {
      Left(providerType)
    }
  }

}
