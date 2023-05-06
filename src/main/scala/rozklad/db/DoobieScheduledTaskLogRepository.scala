package rozklad.db

import doobie.{Fragment, Update}
import rozklad.api.{Id, ScheduledTask, ScheduledTaskLog}
import doobie.implicits._
import cats.implicits._
import doobie.postgres.implicits._

class DoobieScheduledTaskLogRepository(table: String) extends ScheduledTaskLogRepository {
  override def insert(tasks: List[ScheduledTask]): doobie.ConnectionIO[Unit] = {
    val sql =
      s"""
        insert into ${table}(id, task_id, status, created_at, failed_reason, payload, trigger_at)
        values (?, ?, ?, ?, ?, ?, ?)
     """
    Update[ScheduledTaskLog](sql).updateMany(tasks.map(ScheduledTaskLog.from)).void
  }

  override def select(id: Id[ScheduledTask]): doobie.ConnectionIO[List[ScheduledTaskLog]] = {
    (fr"""
        select id, task_id, status, created_at, failed_reason, payload, trigger_at
        from """ ++ Fragment.const(table) ++
      fr"""
        where task_id = $id
        order by created_at
         """).query[ScheduledTaskLog].to[List]
  }

  override def delete(id: Id[ScheduledTask]): doobie.ConnectionIO[List[ScheduledTaskLog]] = {
    (fr"""delete from """ ++ Fragment.const(table) ++ fr""" where task_id = $id""")
      .update
      .withGeneratedKeys[ScheduledTaskLog]("id", "task_id", "status", "created_at", "failed_reason", "payload", "trigger_at")
      .compile
      .toList
  }
}
