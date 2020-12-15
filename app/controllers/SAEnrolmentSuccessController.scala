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
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.selfassessment.SuccessfullyEnrolledView

import scala.concurrent.{ExecutionContext, Future}

class SAEnrolmentSuccessController @Inject()(
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  config: ConfigDecorator,
  successfulEnrolment: SuccessfullyEnrolledView)(
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
          Future.successful(Ok(successfulEnrolment()))
        } else {
          Future.successful(Redirect(controllers.routes.HomeController.index()))
        }
    }

}
