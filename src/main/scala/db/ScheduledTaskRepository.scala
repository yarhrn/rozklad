package rozklad
package db

import api.{Id, ScheduledTask, Status}
import db._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.Instant

object ScheduledTaskRepository {

  def schedule(descriptor: ScheduledTask) =
    sql"""
        insert into
            scheduled_jobs (id, scheduled_at, trigger_at, status, updated_at, payload)
        values
            (${descriptor.id}, ${descriptor.scheduledAt}, ${descriptor.triggerAt}, ${descriptor.status}, ${descriptor.updatedAt}, ${descriptor.payload})
      """
      .update
      .run

  def acquireBatch(now: Instant, limit: Int) = {
    sql"""
        update scheduled_jobs
        set status = ${Status.Acquired}, updated_at = ${now}
        where id in (
            select id
            from scheduled_jobs
            where status = ${Status.Created} and trigger_at < ${now}
            order by scheduled_at
            for update skip locked
            limit ${limit}
        )
        returning id, scheduled_at, trigger_at, status, updated_at, payload
       """
      .query[ScheduledTask]
      .to[List]
  }

  def done(id: Id[ScheduledTask], now: Instant) = {
    sql"""
        update scheduled_jobs
        set status = ${Status.Succeeded}, updated_at = ${now}
        where id = ${id} and status = ${Status.Acquired}
        returning id, scheduled_at, trigger_at, status, updated_at, payload
       """
      .query[ScheduledTask]
      .to[List]
  }


}

