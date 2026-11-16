package rozklad.db

import play.api.libs.json.JsValue
import rozklad.api.{FailedReason, Id, ScheduledTask}

import java.time.Instant

trait ScheduledTaskRepository {
  @throws(classOf[InsertDuplicate])
  def schedule(descriptor: ScheduledTask): org.typelevel.doobie.ConnectionIO[Unit]
  def acquireBatch(now: Instant, limit: Int): org.typelevel.doobie.ConnectionIO[List[ScheduledTask]]
  def delete(id: Id[ScheduledTask]): org.typelevel.doobie.ConnectionIO[Option[ScheduledTask]]
  def select(id: Id[ScheduledTask]): org.typelevel.doobie.ConnectionIO[Option[ScheduledTask]]
  def done(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): org.typelevel.doobie.ConnectionIO[List[ScheduledTask]]
  def reschedule(now: Instant, descriptor: ScheduledTask): org.typelevel.doobie.ConnectionIO[List[ScheduledTask]]

  def failed(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      updatedPayload: Option[JsValue]): org.typelevel.doobie.ConnectionIO[List[ScheduledTask]]
}


case class InsertDuplicate() extends RuntimeException