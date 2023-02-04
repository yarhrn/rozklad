package rozklad.api

import play.api.libs.json.JsValue
import java.time.Instant

trait TaskScheduler[F[_]] {

  def schedule(id: Id[ScheduledTask], triggerAt: Instant, scheduledAt: Instant, payload: JsValue): F[ScheduledTask]

}

case class ReschedulingFailed(id: Id[ScheduledTask], now: Instant)
    extends RuntimeException(s"Rescheduling failed for $id at $now, most likely task is in acquired state")
