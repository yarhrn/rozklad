package rozklad
package api

import play.api.libs.json.JsValue
import java.time.Instant

trait TaskScheduler[F[_]] {

  def schedule(id: Id[ScheduledTask], triggerAt: Instant, scheduledAt: Instant, payload: JsValue): F[ScheduledTask]

}
