package rozklad
package db

import api.{Id, ReschedulingFailed, ScheduledTask, Status, TaskScheduler}

import cats.MonadError
import cats.effect.Clock
import cats.effect.kernel.MonadCancel
import doobie.util.transactor.Transactor
import play.api.libs.json.JsValue
import doobie.implicits._
import cats.implicits._
import doobie.free.connection.ConnectionIO
import rozklad.db.ScheduledTaskRepository.InsertDuplicate

import java.time.Instant

class DoobieTaskScheduler[F[_]](xa: Transactor[F])(implicit ME: MonadCancel[F, Throwable]) extends TaskScheduler[F] {

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
      task <- ScheduledTaskRepository.schedule(task).as(task).recoverWith {
        case _: InsertDuplicate =>
          ScheduledTaskRepository.reschedule(scheduledAt, task).map(_.headOption).flatMap {
            case Some(task) => task.pure[ConnectionIO]
            case None =>
              Clock[ConnectionIO]
                .realTimeInstant
                .flatMap(instant => MonadError[ConnectionIO, Throwable].raiseError(ReschedulingFailed(id, instant)))
          }
      }
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield task
  }.transact(xa)

}
