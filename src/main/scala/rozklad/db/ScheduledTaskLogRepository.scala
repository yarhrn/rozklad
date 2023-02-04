package rozklad.db

import rozklad.api.{FailedReason, Id, ScheduledTask, Status}

import doobie._
import play.api.libs.json.JsValue

import doobie.postgres.implicits._
import doobie.implicits._

import java.time.Instant
import cats.implicits._

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

object ScheduledTaskLogRepository {
  def insert(tasks: List[ScheduledTask]): doobie.ConnectionIO[Int] = {
    val sql = """
        insert into scheduled_tasks_change_log(id, task_id, status, created_at, failed_reason, payload, trigger_at)
        values (?, ?, ?, ?, ?, ?, ?)
     """
    Update[ScheduledTaskLog](sql).updateMany(tasks.map(ScheduledTaskLog.from))
  }

  def logs(id: Id[ScheduledTask]): doobie.ConnectionIO[List[ScheduledTaskLog]] = {
    sql"""
        select id, task_id, status, created_at, failed_reason, payload, trigger_at
        from scheduled_tasks_change_log
        where task_id = $id
        order by created_at
         """.query[ScheduledTaskLog].to[List]
  }

}
