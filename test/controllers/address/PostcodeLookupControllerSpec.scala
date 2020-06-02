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

package controllers.address

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{PostalAddrType, SoleAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.NonFilerSelfAssessmentUser
import models.addresslookup.RecordSet
import models.dto.{AddressFinderDto, AddressPageVisitedDto, ResidencyChoiceDto, TaxCreditsChoiceDto}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.{fakeStreetPafAddressRecord, oneAndTwoOtherPlacePafRecordSet}
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.PostcodeLookupView

import scala.concurrent.{ExecutionContext, Future}

class PostcodeLookupControllerSpec extends BaseSpec with MockitoSugar {

  trait LocalSetup {

    lazy val mockAuthJourney: AuthJourney = mock[AuthJourney]
    lazy val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
    lazy val mockAddressLookupService: AddressLookupService = mock[AddressLookupService]
    lazy val mockAuditConnector: AuditConnector = mock[AuditConnector]

    implicit lazy val ec: ExecutionContext = injected[ExecutionContext]
    implicit lazy val configDecorator: ConfigDecorator = injected[ConfigDecorator]
    implicit lazy val templateRenderer: TemplateRenderer = injected[TemplateRenderer]

    def controller: PostcodeLookupController =
      new PostcodeLookupController(
        mockAddressLookupService,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        mockAuditConnector,
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents],
        injected[PostcodeLookupView],
        injected[DisplayAddressInterstitialView]
      )(injected[LocalPartialRetriever], configDecorator, templateRenderer, ec)

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(false)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    def addressLookupResponse: AddressLookupResponse = AddressLookupSuccessResponse(RecordSet(List()))

    when(mockAddressLookupService.lookup(any(), any())(any())) thenReturn Future.successful(addressLookupResponse)

    when(mockLocalSessionCache.cache(any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(CacheMap("id", Map.empty)))

    when(mockLocalSessionCache.fetch()(any(), any())).thenReturn(Future.successful(sessionCacheResponse))

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = currentRequest[A]
          ).asInstanceOf[UserRequest[A]]
        )
    })

    def pruneDataEvent(dataEvent: DataEvent): DataEvent =
      dataEvent.copy(
        tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token",
        detail = dataEvent.detail - "credId")

    def comparatorDataEvent(dataEvent: DataEvent, auditType: String, postcode: String): DataEvent = DataEvent(
      "pertax-frontend",
      auditType,
      dataEvent.eventId,
      Map("path" -> "/test", "transactionName"         -> "find_address"),
      Map("nino" -> Fixtures.fakeNino.nino, "postcode" -> postcode),
      dataEvent.generatedAt
    )
  }

  "onPageLoad" should {

    "return 200 if the user has entered a residency choice on the previous page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 if the user is on correspondence address journey and has postal address type" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to the beginning of the journey when user has not indicated Residency choice on previous page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "redirect to the beginning of the journey when user has not visited your-address page on correspondence journey" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "verify an audit event has been sent for a user clicking the change address link" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressPageVisitedDto"  -> Json.toJson(AddressPageVisitedDto(true)),
              "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent for a user clicking the change postal address link" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "onSubmit" should {

    "return 404 and log a addressLookupNotFound audit event when an empty record set is returned by the address lookup service" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest("POST", "/test"))

      status(result) shouldBe NOT_FOUND
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupNotFound",
        "AA1 1AA")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to 'Edit your address' when address lookup service is down" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupErrorResponse(new RuntimeException("Some error"))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/edit-address")
    }

    "redirect to the edit address page for a postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/edit-address")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("postalSelectedAddressRecord"), meq(fakeStreetPafAddressRecord))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA")
    }

    "redirect to the edit-address page for a non postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("soleAddressFinderDto" -> Json.toJson(AddressFinderDto("AA1 1AA", None)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType, None)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/edit-address")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("soleSelectedAddressRecord"), meq(fakeStreetPafAddressRecord))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA")
    }

    "redirect to showAddressSelectorForm and log a addressLookupResults audit event when multiple records are returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).getOrElse("") should include("/select-address")

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA")
    }

    "return Not Found when an empty recordset is returned by the address lookup service and back = true" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse = AddressLookupSuccessResponse(RecordSet(List()))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalAddressFinderDto"   -> Json.toJson(AddressFinderDto("AA1 1AA", None)),
              "addressLookupServiceDown" -> Json.toJson(Some(false)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) shouldBe NOT_FOUND
      verify(mockLocalSessionCache, times(1)).cache(any(), any())(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to the postcodeLookupForm and when a single record is returned by the address lookup service and back = true" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType, Some(true))(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/find-address")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockLocalSessionCache, times(1)).cache(any(), any())(any(), any(), any())
    }

    "redirect to showAddressSelectorForm and display the select-address page when multiple records are returned by the address lookup service back=true" in new LocalSetup {
      override def addressLookupResponse: AddressLookupResponse =
        AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).getOrElse("") should include("/select-address")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockLocalSessionCache, times(2)).cache(any(), any())(any(), any(), any())
    }
  }
}