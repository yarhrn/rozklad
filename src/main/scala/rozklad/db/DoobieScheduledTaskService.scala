package rozklad.db

import rozklad.api._

import cats.effect.kernel.MonadCancel
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import play.api.libs.json.JsValue
import rozklad.api.Event._

import java.time.Instant

class DoobieScheduledTaskService[F[_]](xa: Transactor[F], observer: Observer[F])(implicit ME: MonadCancel[F, Throwable])
    extends ScheduledTaskService[F] {

  override def acquireBatch(now: Instant, limit: Int): F[List[ScheduledTask]] = {
    for {
      batch <- ScheduledTaskRepository.acquireBatch(now, limit)
      _ <- ScheduledTaskLogRepository.insert(batch)
    } yield batch
  }.transact(xa)
    .flatTap(tasks =>
      if (tasks.isEmpty) observer.occurred(NoScheduledTasksWereAcquired)
      else observer.occurred(ScheduledTasksAcquired(tasks)))

  override def succeeded(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): F[ScheduledTask] = {
    for {
      task <- ScheduledTaskRepository.done(id, now, payload).validateAndGet(id)
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield task
  }.transact(xa).flatTap(task => observer.occurred(ScheduledTaskDone(task)))

  override def failed(id: Id[ScheduledTask], now: Instant, failedReason: Option[FailedReason], updatedPayload: Option[JsValue]): F[ScheduledTask] = {
    for {
      task <- ScheduledTaskRepository.failed(id, now, failedReason, updatedPayload).validateAndGet(id)
      _ <- ScheduledTaskLogRepository.insert(List(task))
    } yield task
  }.transact(xa).flatTap(task => observer.occurred(ScheduledTaskFailed(task)))

  override def logs(id: Id[ScheduledTask]): fs2.Stream[F, ScheduledTaskLog] = {
    fs2.Stream.eval(ScheduledTaskLogRepository.logs(id).transact(xa)).flatMap(fs2.Stream(_: _*))
  }

  implicit class RichListOfTasks(fa: doobie.ConnectionIO[List[ScheduledTask]]) {
    def validateAndGet(id: Id[ScheduledTask]): doobie.ConnectionIO[ScheduledTask] = {
      fa.ensure(TaskIsNotInExpectedStatusException(id))(_.size == 1).map(_.head)
    }
  }

}