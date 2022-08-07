package rozklad
package api

import cats.effect._
import cats.implicits._
import fs2.concurrent.SignallingRef
import impl.ExecutorService
import cats.effect.implicits._

trait Executor[F[_]] {
  def stop: F[Unit]

  def statistics: F[Statistics]
}

case class Statistics(processed: Int, stopped: Boolean) {
  def inc = copy(processed = processed + 1)
}


object Executor {
  def start[F[_] : Temporal](service: ScheduledTaskService[F], routine: ScheduledTask => F[Unit]) = {
    for {
      stopSignal <- SignallingRef.of(false)
      streamEndDeferred <- Deferred.apply[F, Unit]
      statisticsRef <- Ref.of[F, Statistics](Statistics(0, stopped = false))
      executorService = new ExecutorService[F](service, stopSignal, routine, streamEndDeferred, statisticsRef)
      _ <- executorService.unsafeRoutine.compile.drain.start
    } yield (executorService)
  }
}