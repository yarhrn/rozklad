package rozklad.api

import cats.Monad
import play.api.libs.json.JsValue

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait Observer[F[_]] {

  def occurred(event: Event): F[Unit]

}

object Observer {
  def noop[F[_]: Monad] = (_: Event) => Monad[F].unit
}

sealed trait Event

object Event {

  case class ScheduledTasksAcquired(tasks: List[ScheduledTask]) extends Event

  case class ScheduledTaskDone(task: ScheduledTask) extends Event

  case class ScheduledTaskFailed(task: ScheduledTask) extends Event

  case object NoScheduledTasksWereAcquired extends Event

  case class ExecutorIsAboutToAcquireBatch(now: Instant) extends Event

  case class ExecutorFailedDuringAcquiringBatch(now: Instant, exception: Throwable) extends Event

  case class NothingWasAcquiredLastTimeGoingToSleep(duration: FiniteDuration) extends Event

  case class ExecutionSucceeded(task: ScheduledTask, now: Instant, payload: Option[JsValue]) extends Event

  case class ExecutionFailed(task: ScheduledTask, now: Instant, reason: Option[FailedReason], payload: Option[JsValue]) extends Event

  case class ExecutionErrored(task: ScheduledTask, now: Instant, exception: Throwable) extends Event

  case class ErrorDuringHandlingExecutionResult(task: ScheduledTask, now: Instant, ex: Throwable) extends Event

  case class TaskRescheduled(task: ScheduledTask) extends Event

  case class TaskScheduled(tas: ScheduledTask) extends Event

}
