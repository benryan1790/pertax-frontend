package models

import play.api.libs.json._

case class SaEnrolment(userId: String, action: String)

object SaEnrolment {

  implicit val reads: Reads[SaEnrolment] = Json.reads[SaEnrolment]

  implicit val writes: Writes[SaEnrolment] = (saEnrolment: SaEnrolment) => {
    Json.obj("userId" -> saEnrolment.userId,
      "type" -> "principal",
      "action" -> saEnrolment.action)
  }
}