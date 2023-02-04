package rozklad.impl

import rozklad.api.{
  ExecutorService,
  FailedReason,
  Observer,
  ScheduledTask,
  ScheduledTaskExecutor,
  ScheduledTaskOutcome,
  ScheduledTaskService,
  Statistics,
  TaskScheduler
}
import cats.Monad
import cats.effect.syntax.all._
import cats.effect._
import cats.implicits._
import rozklad.api.Event._
import rozklad.utils.SafeConstruct

import scala.concurrent.duration._

class RoutineExecutorService[F[_]: Async](
    service: ScheduledTaskService[F],
    stoppedRef: Ref[F, Boolean],
    executor: ScheduledTaskExecutor[F],
    streamEndDeferred: Deferred[F, Unit],
    statisticsRef: Ref[F, Statistics],
    acquireBatchSize: Int,
    observer: Observer[F],
    sleepTime: FiniteDuration,
    scheduler: TaskScheduler[F])
    extends ExecutorService[F] {

  val SC = implicitly[SafeConstruct[F]]

  def get(stage: ExecutorRoutineStage) = for {
    _ <- stage match {
      case Sleep =>
        observer.occurred(NothingWasAcquiredLastTimeGoingToSleep(sleepTime)) *> Temporal[F].sleep(sleepTime)
      case Continue => Monad[F].unit
    }
    now <- Temporal[F].realTimeInstant
    _ <- observer.occurred(ExecutorIsAboutToAcquireBatch(now))
    batch <- SC.construct(service.acquireBatch(now, acquireBatchSize)).handleErrorWith { exception =>
      observer.occurred(ExecutorFailedDuringAcquiringBatch(now, exception)) *>
        Monad[F].pure(List.empty)
    }
    _ <- statisticsRef.update(_.copy(lastAcquireAttemptAt = Some(now)))
  } yield {
    if (batch.length < 10) {
      batch -> ExecutorRoutineStage.sleep
    } else {
      batch -> ExecutorRoutineStage.continue
    }
  }

  private def executeTask(task: ScheduledTask) = {
    Temporal[F].realTimeInstant.flatMap { now =>
      MonadCancel[F]
        .guaranteeCase(SC.construct(executor.execute(task)).uncancelable) {
          case Outcome.Succeeded(result) =>
            result.flatMap {
              case ScheduledTaskOutcome.Succeeded(payload) =>
                service.succeeded(task.id, now, payload).void >>
                  observer.occurred(ExecutionSucceeded(task, now, payload))
              case ScheduledTaskOutcome.Failed(reason, payload) =>
                service.failed(task.id, now, reason, payload).void >>
                  observer.occurred(ExecutionFailed(task, now, reason, payload))
              case ScheduledTaskOutcome.Rescheduled(payload, triggerAt) =>
                scheduler.schedule(task.id, triggerAt, task.scheduledAt, payload).void
            }
          case Outcome.Errored(e) =>
            service.failed(task.id, now, Some(FailedReason.Exception), None).void >>
              observer.occurred(ExecutionErrored(task, now, e))
          case Outcome.Canceled() => Monad[F].unit
        }
        .uncancelable
        .void
        .recoverWith(ex => observer.occurred(ErrorDuringHandlingExecutionResult(task, now, ex)))
    }
  }

  private def routineStep(stage: ExecutorRoutineStage): F[Unit] = {
    for {
      getResult <- get(stage)
      (batch, stage) = getResult
      _ <- batch.map(task => executeTask(task)).sequence
      stopped <- stoppedRef.get
      _ <-
        if (stopped) {
          streamEndDeferred.complete(())
        } else {
          routineStep(stage)
        }
    } yield ()
  }

  def unsafeRoutine = routineStep(Continue)

  override def stop: F[Unit] = {
    stoppedRef.set(true) *>
      streamEndDeferred.get
  }

  override def statistics: F[Statistics] = statisticsRef.get

}

sealed trait ExecutorRoutineStage

object ExecutorRoutineStage {
  def sleep: ExecutorRoutineStage = Sleep

  def continue: ExecutorRoutineStage = Continue
}

case object Sleep extends ExecutorRoutineStage

case object Continue extends ExecutorRoutineStage
