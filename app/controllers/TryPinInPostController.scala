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

package controllers

import config.ConfigDecorator
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import javax.inject.Inject
import models.{NonFilerSelfAssessmentUser, SelfAssessmentUser}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.EnrolForSAService
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.selfassessment.TryPinInPost

import scala.concurrent.{ExecutionContext, Future}

class TryPinInPostController @Inject()(
  cc: MessagesControllerComponents,
  config: ConfigDecorator,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  tryPinInPostView: TryPinInPost,
  enrolForSAService: EnrolForSAService,
  errorRenderer: ErrorRenderer)(
  implicit ec: ExecutionContext,
  partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer)
    extends PertaxBaseController(cc) {

  val pinAndPostFeatureToggle: Boolean = config.removePipJourneyEnabled

  def onPageLoad: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request =>
        if (pinAndPostFeatureToggle) {
          Future.successful(Ok(tryPinInPostView()))
        } else {
          Future.successful(Redirect(controllers.routes.HomeController.index()))
        }
    }

  def onSubmit: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request =>
        request.saUserType match {
          case saUser: SelfAssessmentUser =>
            enrolForSAService.enrolOnly(saUser.saUtr.utr, request.groupId, request.credId)
          case NonFilerSelfAssessmentUser => {
            Logger.warn("User had no sa account when one was required")
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)
          }
        }
    }
}
