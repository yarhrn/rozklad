package rozklad
package db

import api.{FailedReason, Id, ScheduledTask, Status}
import db._

import cats.free.Free
import doobie.free.connection
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import play.api.libs.json.JsValue

import java.time.Instant

object ScheduledTaskRepository {

  def schedule(descriptor: ScheduledTask): doobie.ConnectionIO[Int] =
    sql"""
        insert into
            scheduled_tasks (id, scheduled_at, trigger_at, status, updated_at, payload)
        values
            (${descriptor.id}, ${descriptor.scheduledAt}, ${descriptor.triggerAt}, ${descriptor.status}, ${descriptor.updatedAt}, ${descriptor.payload})
      """.update.run

  def acquireBatch(
      now: Instant,
      limit: Int): Free[connection.ConnectionOp, List[ScheduledTask]] = {
    sql"""
        update scheduled_tasks
        set status = ${Status.Acquired}, updated_at = $now
        where id in (
            select id
            from scheduled_tasks
            where status = ${Status.Created} and trigger_at < $now
            order by scheduled_at
            for update skip locked
            limit $limit
        )
        returning id, scheduled_at, trigger_at, status, updated_at, failed_reason, payload
       """.query[ScheduledTask].to[List].map(_.sortBy(_.scheduledAt))
  }

  def done(
      id: Id[ScheduledTask],
      now: Instant,
      payload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      id = id,
      now = now,
      failedReason = None,
      payload = payload,
      fromStatus = Status.Acquired,
      toStatus = Status.Succeeded
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
      fromStatus = Status.Acquired,
      toStatus = Status.Failed
    )
  }

  def transition(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      payload: Option[JsValue],
      fromStatus: Status,
      toStatus: Status): doobie.ConnectionIO[List[ScheduledTask]] = {
    (fr"""
        update scheduled_tasks
        set status = $toStatus, updated_at = $now""" ++
      failedReason
        .map(failedReason => fr", failed_reason = $failedReason")
        .getOrElse(Fragment.empty) ++
      payload.map(payload => fr",payload = $payload ").getOrElse(Fragment.empty) ++ fr"""
        where id = $id and status = $fromStatus
        returning id, scheduled_at, trigger_at, status, updated_at, failed_reason, payload
       """).query[ScheduledTask].to[List]
  }

}
