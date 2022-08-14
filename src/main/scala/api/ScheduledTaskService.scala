package rozklad
package api

import play.api.libs.json.JsValue
import rozklad.db.ScheduledTaskLog

import java.time.Instant

trait ScheduledTaskService[F[_]] {
  def schedule(descriptor: ScheduledTask): F[ScheduledTask]

  def acquireBatch(now: Instant, limit: Int): F[List[ScheduledTask]]

  def done(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): F[ScheduledTask]

  def failed(id: Id[ScheduledTask], now: Instant, failedReason: Option[FailedReason], payload: Option[JsValue]): F[ScheduledTask]

  def logs(id: Id[ScheduledTask]): F[List[ScheduledTaskLog]]
}

case class TaskIsNotInExpectedStatusException(id: Id[ScheduledTask]) extends RuntimeException(s"Task ${id} is not in acquired state")
