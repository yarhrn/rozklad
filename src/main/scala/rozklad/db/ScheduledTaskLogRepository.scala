package rozklad.db

import rozklad.api.{Id, ScheduledTask, ScheduledTaskLog}

trait ScheduledTaskLogRepository {
  def insert(tasks: List[ScheduledTask]): org.typelevel.doobie.ConnectionIO[Unit]
  def select(id: Id[ScheduledTask]): org.typelevel.doobie.ConnectionIO[List[ScheduledTaskLog]]
  def delete(id: Id[ScheduledTask]): org.typelevel.doobie.ConnectionIO[List[ScheduledTaskLog]]
}