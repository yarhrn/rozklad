package rozklad
package api

import cats.effect._
import cats.implicits._
import impl.Fs2ExecutorService

import cats.effect.implicits._
import play.api.libs.json.JsValue

import scala.concurrent.duration.FiniteDuration

trait ExecutorService[F[_]] {
  def stop: F[Unit]

  def statistics: F[Statistics]
}

trait Executor[F[_]] {
  def execute(task: ScheduledTask): F[ScheduledTaskOutcome]
}

sealed trait ScheduledTaskOutcome

object ScheduledTaskOutcome {

  case class Succeeded(payload: Option[JsValue]) extends ScheduledTaskOutcome

  case class Failed(reason: Option[FailedReason], payload: Option[JsValue])
      extends ScheduledTaskOutcome

  object Succeeded {
    val empty: ScheduledTaskOutcome = Succeeded(None)
  }

  object Failed {
    val empty: ScheduledTaskOutcome = Failed(None, None)
  }

}

case class Statistics(processed: Int, stopped: Boolean) {
  def inc: Statistics = copy(processed = processed + 1)
}

object ExecutorService {
  def start[F[_]: Async](
      service: ScheduledTaskService[F],
      routine: Executor[F],
      observer: Observer[F],
      sleepTime: FiniteDuration): F[Fs2ExecutorService[F]] = {
    for {
      stopSignal <- Ref.of(false)
      streamEndDeferred <- Deferred.apply[F, Unit]
      statisticsRef <- Ref.of[F, Statistics](Statistics(0, stopped = false))
      executorService = new Fs2ExecutorService[F](
        service,
        stopSignal,
        routine,
        streamEndDeferred,
        statisticsRef,
        10,
        observer,
        sleepTime)
      _ <- executorService.unsafeRoutine.start
    } yield executorService
  }
}
