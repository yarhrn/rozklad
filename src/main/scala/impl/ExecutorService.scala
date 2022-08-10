package rozklad
package impl

import api.{Executor, FailedReason, Observer, ScheduledTask, ScheduledTaskService, Statistics}

import cats.Monad
import cats.effect.syntax.all._
import cats.effect._
import cats.implicits._
import fs2.Chunk
import fs2.concurrent.SignallingRef
import org.log4s.Info

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._


class ExecutorService[F[_] : Temporal](service: ScheduledTaskService[F],
                                       stopSignal: SignallingRef[F, Boolean],
                                       routine: ScheduledTask => F[Unit],
                                       streamEndDeferred: Deferred[F, Unit],
                                       statisticsRef: Ref[F, Statistics],
                                       acquireBatchSize: Int) extends Executor[F] {

  private val get = for {
    now <- Temporal[F].realTimeInstant
    batch <- service.acquireBatch(now, acquireBatchSize)
  } yield {
    if (batch.length < 10) {
      batch -> Option(ExecutorRoutineStage.sleep)
    } else {
      batch -> Option(ExecutorRoutineStage.continue)
    }
  }

  private val updateStatisticsOnStop = streamEndDeferred.get.flatMap(_ => statisticsRef.update(_.copy(stopped = true)))

  val unsafeRoutine: fs2.Stream[F, Unit] = {
    fs2.Stream.eval(updateStatisticsOnStop) *>
      fs2.Stream.unfoldLoopEval(Continue: ExecutorRoutineStage) {
        case Sleep =>
          Temporal[F].sleep(10.seconds) *> get
        case Continue => get
      }.flatMap(batch => fs2.Stream.chunk(Chunk(batch: _*)))
        .evalMap { task =>
          MonadCancel[F].bracketCase(Monad[F].pure(task))(task => routine(task).uncancelable) {
            (task, outcome) =>
              (outcome match {
                case Outcome.Succeeded(_) =>
                  for {
                    instant <- Temporal[F].realTimeInstant
                    _ <- service.done(task.id, instant)
                  } yield ()
                case Outcome.Errored(e) =>
                  for {
                    instant <- Temporal[F].realTimeInstant
                    _ <- service.failed(task.id, instant, Some(FailedReason.Exception), None)
                  } yield ()
                case Outcome.Canceled() => Monad[F].unit
              }) *> statisticsRef.update(_.inc)
          }.uncancelable
        }
        .interruptWhen(stopSignal)
        .onFinalize(streamEndDeferred.complete(()).void)
  }

  override def stop: F[Unit] = {
    stopSignal.set(true)
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
