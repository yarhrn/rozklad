package rozklad
package db

import api.{Id, ScheduledTask, Status}
import doobie._
import play.api.libs.json.JsValue
import db._
import doobie.postgres.implicits._
import java.time.Instant
import cats.implicits._

case class ScheduledTaskLog(id: Id[ScheduledTaskLog],
                            jobId: Id[ScheduledTask],
                            status: Status,
                            createdAt: Instant,
                            payload: JsValue)

object ScheduledTaskLog {
  def from(task: ScheduledTask) = {
    ScheduledTaskLog(
      Id.random,
      task.id,
      task.status,
      task.updatedAt,
      task.payload
    )
  }
}

object ScheduledTaskLogRepository {
  def insert(tasks: List[ScheduledTask]): doobie.ConnectionIO[Int] = {
    val sql ="""
        insert into scheduled_jobs_change_log(id, job_id, status, created_at, payload)
        values (?, ?, ?, ?, ?)
     """
    Update[ScheduledTaskLog](sql).updateMany(tasks.map(ScheduledTaskLog.from))
  }
}
