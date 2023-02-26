package rozklad.api

import cats.effect._
import cats.implicits._
import cats.Monad
import cats.effect.implicits._
import play.api.libs.json.JsValue
import rozklad.impl.RoutineExecutorService

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait ExecutorService[F[_]] {
  def stop: F[Unit]

  def statistics: F[Statistics]
}

trait ScheduledTaskExecutor[F[_]] {
  def execute(task: ScheduledTask): F[ScheduledTaskOutcome]
}

object ScheduledTaskExecutor {
  import cats.implicits._

  def compose[F[_]: Monad](executors: List[ScheduledTaskExecutor[F]]) = new ScheduledTaskExecutor[F] {
    override def execute(task: ScheduledTask): F[ScheduledTaskOutcome] = {
      def step(executors: List[ScheduledTaskExecutor[F]]): F[ScheduledTaskOutcome] = {
        executors match {
          case head :: next =>
            head.execute(task).flatMap {
              case ScheduledTaskOutcome.Failed(Some(FailedReason.NotSupported), _) =>
                step(next)
              case res => Monad[F].pure(res)
            }
          case Nil => Monad[F].pure(ScheduledTaskOutcome.Failed.notSupported)
        }
      }
      step(executors)
    }
  }
}

sealed trait ScheduledTaskOutcome

object ScheduledTaskOutcome {

  case class Succeeded(payload: Option[JsValue]) extends ScheduledTaskOutcome

  case class Failed(reason: Option[FailedReason], payload: Option[JsValue]) extends ScheduledTaskOutcome

  case class Rescheduled(payload: JsValue, triggerAt: Instant) extends ScheduledTaskOutcome

  object Succeeded {
    val empty: ScheduledTaskOutcome = Succeeded(None)
  }

  object Failed {
    val empty: ScheduledTaskOutcome = Failed(None, None)
    val notSupported: Failed = Failed(Some(FailedReason.NotSupported), None)
  }

}

case class Statistics(processed: Int, stopped: Boolean, lastAcquireAttemptAt: Option[Instant], startedAt: Instant) {
  def inc: Statistics = copy(processed = processed + 1)
}

object ExecutorService {
  def start[F[_]: Async](
      service: ScheduledTaskService[F],
      routine: ScheduledTaskExecutor[F],
      observer: Observer[F],
      sleepTime: FiniteDuration,
      scheduler: TaskScheduler[F]): F[RoutineExecutorService[F]] = {
    for {
      now <- Temporal[F].realTimeInstant
      stopSignal <- Ref.of(false)
      streamEndDeferred <- Deferred.apply[F, Unit]
      statisticsRef <- Ref.of[F, Statistics](Statistics(0, stopped = false, None, now))
      executorService = new RoutineExecutorService[F](
        service,
        stopSignal,
        routine,
        streamEndDeferred,
        statisticsRef,
        10,
        observer,
        sleepTime,
        scheduler)
      _ <- executorService.unsafeRoutine.start
    } yield executorService
  }
}
