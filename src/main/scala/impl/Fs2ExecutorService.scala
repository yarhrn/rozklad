package rozklad
package impl

import api.{Executor, ExecutorService, FailedReason, Observer, ScheduledTask, ScheduledTaskOutcome, ScheduledTaskService, Statistics}

import cats.{Eval, Monad, MonadError}
import cats.effect.syntax.all._
import cats.effect._
import cats.implicits._
import rozklad.api.Event._
import rozklad.utils.SafeConstruct

import scala.concurrent.duration._

class Fs2ExecutorService[F[_]: Async](
    service: ScheduledTaskService[F],
    stoppedRef: Ref[F, Boolean],
    executor: Executor[F],
    streamEndDeferred: Deferred[F, Unit],
    statisticsRef: Ref[F, Statistics],
    acquireBatchSize: Int,
    observer: Observer[F],
    sleepTime: FiniteDuration)
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
  } yield {
    if (batch.length < 10) {
      batch -> ExecutorRoutineStage.sleep
    } else {
      batch -> ExecutorRoutineStage.continue
    }
  }

  private def executeTask(task: ScheduledTask) = {
    MonadCancel[F]
      .guaranteeCase(executor.execute(task).uncancelable) { outcome =>
        for {
          now <- Temporal[F].realTimeInstant
          _ <- outcome match {
            case Outcome.Succeeded(result) =>
              result.flatMap {
                case ScheduledTaskOutcome.Succeeded(payload) =>
                  observer.occurred(ExecutionSucceeded(task.id, now, payload)) >>
                    service.done(task.id, now, payload)
                case ScheduledTaskOutcome.Failed(reason, payload) =>
                  observer.occurred(ExecutionFailed(task.id, now, reason, payload)) >>
                    service.failed(task.id, now, reason, payload)
              }
            case Outcome.Errored(e) =>
              observer.occurred(ExecutionErrored(task.id, now, e)) >>
                service.failed(task.id, now, Some(FailedReason.Exception), None)
            case Outcome.Canceled() => ???
          }
        } yield ()
      }
      .uncancelable
      .void
  }.recover(_ => Monad[F].unit) // improve here???

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
