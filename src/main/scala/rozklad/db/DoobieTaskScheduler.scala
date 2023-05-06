package rozklad.db

import rozklad.api.{Id, Observer, ReschedulingFailed, ScheduledTask, Status, TaskScheduler}
import cats.MonadError
import cats.effect.Clock
import cats.effect.kernel.MonadCancel
import doobie.util.transactor.Transactor
import play.api.libs.json.JsValue
import doobie.implicits._
import cats.implicits._
import doobie.free.connection.ConnectionIO
import rozklad.api.Event.{TaskRescheduled, TaskScheduled}
import rozklad.api.Status.Created

import java.time.Instant

class DoobieTaskScheduler[F[_]](
    xa: Transactor[F],
    observer: Observer[F],
    scheduledTaskRepository: ScheduledTaskRepository = new DoobieScheduledTaskRepository(DefaultScheduledTasksTableName),
    scheduledTaskLogsRepository: ScheduledTaskLogRepository = new DoobieScheduledTaskLogRepository(DefaultScheduledTasksLogsTableName))(implicit ME: MonadCancel[F, Throwable])
    extends TaskScheduler[F] {

  override def schedule(id: Id[ScheduledTask], triggerAt: Instant, scheduledAt: Instant, payload: JsValue): F[ScheduledTask] = {
    val task = ScheduledTask(
      id,
      scheduledAt,
      triggerAt,
      Status.Created,
      scheduledAt,
      None,
      payload
    )

    for {
      task <- scheduledTaskRepository.schedule(task).as(task).recoverWith {
        case _: InsertDuplicate =>
          scheduledTaskRepository.reschedule(scheduledAt, task).map(_.headOption).flatMap {
            case Some(task) => task.pure[ConnectionIO]
            case None =>
              Clock[ConnectionIO].realTimeInstant.flatMap(instant => MonadError[ConnectionIO, Throwable].raiseError(ReschedulingFailed(id, instant)))
          }
      }
      _ <- scheduledTaskLogsRepository.insert(List(task))
    } yield task
  }.transact(xa).flatTap { task =>
    if (task.status == Created) {
      observer.occurred(TaskScheduled(task))
    } else {
      observer.occurred(TaskRescheduled(task))
    }
  }

}
