package rozklad
package db

import api._
import cats.effect.kernel.MonadCancel
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import java.time.Instant

class DoobieScheduledTaskService[F[_]](xa: Transactor[F])(implicit ME: MonadCancel[F, Throwable]) extends ScheduledTaskService[F] {

  override def schedule(task: ScheduledTask): F[Unit] = {
    for {
      _ <- ScheduledTaskRepository.schedule(task)
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield ()
  }.transact(xa)


  override def acquireBatch(now: Instant, limit: Int) = {
    for {
      batch <- ScheduledTaskRepository.acquireBatch(now, limit)
      _ <- ScheduledTaskLogRepository.insert(batch)
    } yield (batch)
  }.transact(xa)

  override def done(id: Id[ScheduledTask], now: Instant): F[ScheduledTask] = {
    for {
      task <- ScheduledTaskRepository.done(id, now)
        .ensureOr(rows => new RuntimeException(s"Updated rows is not 1 but ${rows} for ${id}"))(_.size == 1)
        .map(_.head)
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield (task)
  }.transact(xa)

  override def failed(id: Id[ScheduledTask], failedReason: Option[FailedReason]): F[ScheduledTask] = ???
}

