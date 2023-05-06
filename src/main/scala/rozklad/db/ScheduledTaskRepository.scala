package rozklad.db

import play.api.libs.json.JsValue
import rozklad.api.{FailedReason, Id, ScheduledTask}

import java.time.Instant

trait ScheduledTaskRepository {
  @throws(classOf[InsertDuplicate])
  def schedule(descriptor: ScheduledTask): doobie.ConnectionIO[Unit]
  def acquireBatch(now: Instant, limit: Int): doobie.ConnectionIO[List[ScheduledTask]]
  def delete(id: Id[ScheduledTask]): doobie.ConnectionIO[Option[ScheduledTask]]
  def select(id: Id[ScheduledTask]): doobie.ConnectionIO[Option[ScheduledTask]]
  def done(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]]
  def reschedule(now: Instant, descriptor: ScheduledTask): doobie.ConnectionIO[List[ScheduledTask]]

  def failed(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      updatedPayload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]]
}


case class InsertDuplicate() extends RuntimeException