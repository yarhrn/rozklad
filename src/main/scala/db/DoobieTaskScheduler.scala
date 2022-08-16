package rozklad
package db

import api.{Id, ScheduledTask, Status, TaskScheduler}

import cats.effect.kernel.MonadCancel
import doobie.util.transactor.Transactor
import play.api.libs.json.JsValue
import doobie.implicits._

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
      _ <- ScheduledTaskRepository.schedule(task)
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield task
  }.transact(xa)

}
