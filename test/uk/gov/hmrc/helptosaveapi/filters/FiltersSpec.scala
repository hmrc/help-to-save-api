/*
 * Copyright 2019 HM Revenue & Customs
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

import com.kenshoo.play.metrics.MetricsFilter
import uk.gov.hmrc.helptosaveapi.util.TestSupport
import uk.gov.hmrc.play.bootstrap.filters._

class FiltersSpec extends TestSupport {

  val mockMDCFilter = new MDCFilter(fakeApplication.materializer, fakeApplication.configuration)

  class EmptyMicroserviceFilters extends MicroserviceFilters(
    stub[MetricsFilter],
    stub[AuditFilter],
    stub[LoggingFilter],
    stub[CacheControlFilter],
    mockMDCFilter
  )

  val mockMicroServiceFilters = mock[MicroserviceFilters]
  val mockXSSProtectionFilter = mock[XSSProtectionFilter]

  val filters = new Filters(mockMicroServiceFilters, mockXSSProtectionFilter)

  "Filters" must {

    "include the XSSProtectionFilter" in {
      filters.filters should contain(mockXSSProtectionFilter)
    }

  }

}
