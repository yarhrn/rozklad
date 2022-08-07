package rozklad
package api

import java.time.Instant

trait ScheduledTaskService[F[_]] {
  def schedule(descriptor: ScheduledTask): F[Unit]

  def acquireBatch(now: Instant, limit: Int): F[List[ScheduledTask]]

  def done(id: Id[ScheduledTask], now: Instant): F[ScheduledTask]

  def failed(id: Id[ScheduledTask], failedReason: Option[FailedReason]): F[ScheduledTask]
}
