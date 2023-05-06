package rozklad.api


trait MaintenanceService[F[_]] {

  def remove(id: Id[ScheduledTask]): F[RemovedScheduledTask]

}

case class RemovedScheduledTask(task: Option[ScheduledTask], logs: List[ScheduledTaskLog])
