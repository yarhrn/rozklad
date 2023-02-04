package rozklad.test.fixture

import java.time.Instant
import rozklad.api.{FailedReason, Id, ScheduledTask, Status}

object ScheduledTaskFixture {

  def someTask(
      id: Id[ScheduledTask] = Id.random,
      scheduledAt: Instant = Instant.now(),
      triggerAt: Instant = Instant.now(),
      status: Status = Status.Created,
      updatedAt: Instant = Instant.now(),
      failedReason: Option[FailedReason] = None) = {
    ScheduledTask(id, scheduledAt, triggerAt, status, if (status == Status.Created) scheduledAt else updatedAt, failedReason)
  }

}
