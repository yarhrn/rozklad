package rozklad
package db

import api.{FailedReason, Id, ScheduledTask, Status}
import db._

import cats.Monad
import cats.free.Free
import doobie.free.connection
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments
import play.api.libs.json.JsValue
import cats.implicits._
import doobie.free.connection.ConnectionIO

import java.sql.SQLException
import java.time.Instant

object ScheduledTaskRepository {

  def schedule(descriptor: ScheduledTask): doobie.ConnectionIO[Int] =
    sql"""
        insert into
            scheduled_tasks (id, scheduled_at, trigger_at, status, updated_at, payload)
        values
            (${descriptor.id}, ${descriptor.scheduledAt}, ${descriptor.triggerAt}, ${descriptor.status}, ${descriptor.updatedAt}, ${descriptor.payload})
        on conflict do nothing
      """.update.run.flatMap { result => if (result == 0) InsertDuplicate().raiseError[ConnectionIO, Int] else result.pure[ConnectionIO] }

  def acquireBatch(now: Instant, limit: Int): Free[connection.ConnectionOp, List[ScheduledTask]] = {
    sql"""
        update scheduled_tasks
        set status = ${Status.Acquired}, updated_at = $now
        where id in (
            select id
            from scheduled_tasks
            where (status = ${Status.Created} or status = ${Status.Rescheduled}) and trigger_at < $now
            order by scheduled_at
            for update skip locked
            limit $limit
        )
        returning id, scheduled_at, trigger_at, status, updated_at, failed_reason, payload
       """.query[ScheduledTask].to[List].map(_.sortBy(_.scheduledAt))
  }

  def done(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      id = id,
      now = now,
      failedReason = None,
      payload = payload,
      fromStatus = List(Status.Acquired),
      toStatus = Status.Succeeded,
      None,
      None
    )
  }
  def reschedule(now: Instant, descriptor: ScheduledTask): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      descriptor.id,
      now,
      None,
      Option(descriptor.payload),
      List(
        Status.Created,
        Status.Rescheduled,
        Status.Failed,
        Status.Succeeded
      ),
      Status.Rescheduled,
      Some(descriptor.triggerAt),
      Some(descriptor.scheduledAt)
    )
  }

  def failed(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      updatedPayload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      id = id,
      now = now,
      failedReason = failedReason,
      payload = updatedPayload,
      fromStatus = List(Status.Acquired),
      toStatus = Status.Failed,
      None,
      None
    )
  }

  def transition(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      payload: Option[JsValue],
      fromStatus: List[Status],
      toStatus: Status,
      triggerAt: Option[Instant],
      scheduledAt: Option[Instant]): doobie.ConnectionIO[List[ScheduledTask]] = {

    val update = fr"""update scheduled_tasks"""

    val returning = fr"returning id, scheduled_at, trigger_at, status, updated_at, failed_reason, payload"

    val where = fragments.whereAnd(
      fr"id = $id ",
      fragments.or(fromStatus.map(status => fr""" status = $status """): _*)
    )

    val set = fragments.setOpt(
      Option(fr"status = $toStatus"),
      Option(fr"updated_at = $now"),
      triggerAt.map(triggerAt => fr"trigger_at = $triggerAt"),
      scheduledAt.map(scheduledAt => fr"scheduled_at = $scheduledAt"),
      failedReason.map(failedReason => fr"failed_reason = $failedReason"),
      payload.map(payload => fr"payload = $payload ")
    )

    val query = update ++
      set ++
      where ++
      returning

    query.query[ScheduledTask].to[List]
  }

  case class InsertDuplicate() extends RuntimeException

}
