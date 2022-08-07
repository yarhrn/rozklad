package rozklad
package db

import api.{Id, ScheduledTask, Status}
import test.EmbeddedPosrtesqlDBEnv

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec

import java.time.Instant
import java.util.UUID

class DoobieScheduledTaskServiceTest extends AnyFlatSpec with EmbeddedPosrtesqlDBEnv {

  "DoobieScheduler" should "dsfsdf" in new ctx {
    val task = ScheduledTask(
      id = Id.random,
      scheduledAt = Instant.now(),
      triggerAt = Instant.now(),
      status = Status.Created,
      updatedAt = Instant.now()
    )
    scheduler.schedule(task).r
    val now: Instant = Instant.now()
    assert(scheduler.acquireBatch(now, 10).r == List(
      task.copy(status = Status.Acquired, updatedAt = now)
    ))

    val now2 = Instant.now()
    assert(scheduler.done(task.id, now2).r == task.copy(status = Status.Succeeded, updatedAt = now2))
    assert(true)
  }


  trait ctx {
    val scheduler = new DoobieScheduledTaskService[IO](xa("scheduled_jobs", "scheduled_jobs_change_log"))
  }

}
