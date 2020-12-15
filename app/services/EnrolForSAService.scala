package services

import connectors.TaxEnrolmentsConnector
import controllers.Assets.Redirect
import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAService @Inject()(connector: TaxEnrolmentsConnector) {

  def enrolAndOrActive(utr: String, credId: String, groupId: String, action: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    connector.enrolForSa(utr, credId, groupId, action).map{
      case true => Redirect(success)
      case _ => Redirect(failure)
    }
  }

}
