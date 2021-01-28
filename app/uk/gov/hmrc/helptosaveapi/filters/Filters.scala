/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveapi.filters

import com.google.inject.{Inject, Singleton}
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.bootstrap.filters.MicroserviceFilters

@Singleton
class Filters @Inject() (
  microserviceFilters: MicroserviceFilters,
  xssProtectionFilter: XSSProtectionFilter
) extends HttpFilters {

  override val filters: Seq[EssentialFilter] = microserviceFilters.filters :+ xssProtectionFilter

}
