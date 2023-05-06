package rozklad
package api

import play.api.libs.json.JsValue

import java.time.Instant

trait ScheduledTaskService[F[_]] {

  def acquireBatch(now: Instant, limit: Int): F[List[ScheduledTask]]

  def succeeded(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): F[ScheduledTask]

  def failed(id: Id[ScheduledTask], now: Instant, failedReason: Option[FailedReason], payload: Option[JsValue]): F[ScheduledTask]

  def logs(id: Id[ScheduledTask]): fs2.Stream[F, ScheduledTaskLog]

  def task(id: Id[ScheduledTask]): F[Option[ScheduledTask]]

}

case class TaskIsNotInExpectedStatusException(id: Id[ScheduledTask]) extends RuntimeException(s"Task ${id} is not in acquired state")

case class ScheduledTaskLog(
                             id: Id[ScheduledTaskLog],
                             taskId: Id[ScheduledTask],
                             status: Status,
                             createdAt: Instant,
                             failedReason: Option[FailedReason],
                             payload: JsValue,
                             triggerAt: Instant)

object ScheduledTaskLog {
  def from(task: ScheduledTask): ScheduledTaskLog = {
    import task._
    ScheduledTaskLog(
      Id.random,
      id,
      status,
      updatedAt,
      failedReason,
      payload,
      triggerAt
    )
  }
}