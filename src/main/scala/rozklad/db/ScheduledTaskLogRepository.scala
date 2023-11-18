package rozklad.db

import rozklad.api.{Id, ScheduledTask, ScheduledTaskLog}

trait ScheduledTaskLogRepository {
  def insert(tasks: List[ScheduledTask]): doobie.ConnectionIO[Unit]
  def select(id: Id[ScheduledTask]): doobie.ConnectionIO[List[ScheduledTaskLog]]
  def delete(id: Id[ScheduledTask]): doobie.ConnectionIO[List[ScheduledTaskLog]]
}
