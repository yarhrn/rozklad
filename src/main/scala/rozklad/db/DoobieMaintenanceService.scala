package rozklad.db

import cats.effect.kernel.MonadCancel
import org.typelevel.doobie.util.transactor.Transactor
import rozklad.api.{Id, MaintenanceService, RemovedScheduledTask, ScheduledTask, Status}
import org.typelevel.doobie.implicits._

class DoobieMaintenanceService[F[_]](
    xa: Transactor[F],
    scheduledTaskRepository: ScheduledTaskRepository = new DoobieScheduledTaskRepository(DefaultScheduledTasksTableName),
    scheduledTaskLogsRepository: ScheduledTaskLogRepository = new DoobieScheduledTaskLogRepository(DefaultScheduledTasksLogsTableName))(
    implicit ME: MonadCancel[F, Throwable])
    extends MaintenanceService[F] {
  override def remove(id: Id[ScheduledTask]): F[RemovedScheduledTask] = {
    for {
      scheduledTask <- scheduledTaskRepository.delete(id)
      logs <- scheduledTaskLogsRepository.delete(id)
    } yield RemovedScheduledTask(scheduledTask, logs)
  }.transact(xa)
}
