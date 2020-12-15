package controllers

import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext

class EnrolForSAController @Inject()(cc: MessagesControllerComponents,
                                     authJourney: AuthJourney,
                                     withBreadcrumbAction: WithBreadcrumbAction)
                                    (implicit ec: ExecutionContext) extends PertaxBaseController(cc) {

  def enrolAndActivate(): Action[AnyContent] = (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
    implicit request =>
    ???
  }



}
