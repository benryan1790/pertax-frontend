package connectors

import config.ConfigDecorator
import javax.inject.Inject
import models.SaEnrolment
import play.api.Logger
import play.api.http.Status.CREATED
import uk.gov.hmrc.http.UpstreamErrorResponse.Upstream4xxResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class TaxEnrolmentsConnector @Inject()(appConfig: ConfigDecorator, http: HttpClient) {

  val serviceUrl: String = appConfig.enrolForSaUrl

  def enrolForSa(utr: String, credId: String, groupId: String, action: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Boolean]= {
    val saEnrolment: SaEnrolment = new SaEnrolment(credId, action)
    http.POST[SaEnrolment, HttpResponse](s"$serviceUrl$groupId/enrolments/IR-SA~UTR~$utr", saEnrolment).map { response =>
      response.status match {
        case CREATED => true
        case _ =>
          Logger.error(s"[TaxEnrolmentsConnector][enrolForSa] failed with status ${response.status}, body: ${response.body}")
          false
      }
    }.recover {
      case Upstream4xxResponse(error) =>
        Logger.error(s"[TaxEnrolmentsConnector][enrolForSa] Enrolment Store Proxy status ${error.statusCode}, message ${error.message}")
        false
      case exception =>
        Logger.error("[TaxEnrolmentsConnector][enrolForSa]Enrolment Store Proxy error", exception)
        false
    }
  }
}
