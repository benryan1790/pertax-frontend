/*
 * Copyright 2020 HM Revenue & Customs
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

package models.addressLookup

import models.addresslookup.{Address, AddressRecord, Country}
import uk.gov.hmrc.play.test.UnitSpec

class AddressRecordSpec extends UnitSpec {

  "calling AddressRecord.validAddress" should {

    "return true where the address has at least one line" in {
      val addressLines = List("some line")
      val validAddress = Address(addressLines, None, None, "Some Postcode", Country("", ""), None)
      AddressRecord("some id", validAddress, "en").isValid shouldBe true
    }

    "return false where the address has no lines" in {
      val noAddressLines = List()
      val invalidAddress = Address(noAddressLines, None, None, "Some Postcode", Country("", ""), None)
      AddressRecord("some id", invalidAddress, "en").isValid shouldBe false
    }

    "return false where country code is more than 2 chars" in {
      val addressLines = List("some line")
      val validAddress = Address(addressLines, None, None, "Some Postcode", Country("", ""), None)
      AddressRecord("some id", validAddress, "ena").isValid shouldBe false
    }
    "return false where country code is less than 2 chars" in {
      val addressLines = List("some line")
      val validAddress = Address(addressLines, None, None, "Some Postcode", Country("", ""), None)
      AddressRecord("some id", validAddress, "e").isValid shouldBe false
    }

    "return false where country code is an emptry string" in {
      val addressLines = List("some line")
      val validAddress = Address(addressLines, None, None, "Some Postcode", Country("", ""), None)
      AddressRecord("some id", validAddress, "").isValid shouldBe false
    }

  }
}