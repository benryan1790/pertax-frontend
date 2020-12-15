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

package services

import connectors.TaxEnrolmentsConnector
import controllers.Assets.Redirect
import error.ErrorRenderer
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAService @Inject()(connector: TaxEnrolmentsConnector, errorRenderer: ErrorRenderer) {

  def enrolAndActivate(utr: String, credId: String, groupId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    connector.enrolForSa(utr, credId, groupId, "enrolAndActivate").map {
      case true => Redirect(controllers.routes.SAEnrolmentSuccessController.onPageLoad())
      case _    => Redirect(controllers.routes.TryPinInPostController.onPageLoad())
    }

  def enrolOnly(utr: String, credId: String, groupId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    connector.enrolForSa(utr, credId, groupId, "enrolOnly").map {
      case true => Redirect("AccesRequestedController")
      case _ => {
        Logger.warn("User had no sa account when one was required")
        errorRenderer.futureError(INTERNAL_SERVER_ERROR)
      }
    }
}
