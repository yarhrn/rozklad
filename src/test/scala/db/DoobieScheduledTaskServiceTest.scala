package rozklad
package db

import api.{Event, Observer, ScheduledTaskService, Status, TaskIsNotInExpectedStatusException}
import test.EmbeddedPosrtesqlDBEnv
import test.fixture.ScheduledTaskFixture
import test.matcher.ScheduledTaskLogMatchers

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import play.api.libs.json.Json
import rozklad.api.Event

import java.time.Instant

class DoobieScheduledTaskServiceTest extends AnyFlatSpec with EmbeddedPosrtesqlDBEnv with ScheduledTaskLogMatchers {

  "DoobieScheduler" should "handle happy path" in new ctx {
    val task = ScheduledTaskFixture.someTask()
    scheduler.schedule(task).r

    val acquiredAt = Instant.now()
    assert(scheduler.acquireBatch(acquiredAt, 10).r == List(task.copy(status = Status.Acquired, updatedAt = acquiredAt)))

    val succeededAt = Instant.now()
    assert(scheduler.done(task.id, succeededAt).r == task.copy(status = Status.Succeeded, updatedAt = succeededAt))

    val logs = scheduler.logs(task.id).r

    logs should have length (3)

    val List(created, acquired, succeeded) = logs
    created should beLike(task.id, Status.Created, task.scheduledAt)
    acquired should beLike(task.id, Status.Acquired, acquiredAt)
    succeeded should beLike(task.id, Status.Succeeded, succeededAt)
  }

  it should "acquire two tasks" in new ctx {
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    val task1 = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    scheduler.schedule(ScheduledTaskFixture.someTask()).r


    val acquireAt = Instant.now()
    assert(scheduler.acquireBatch(acquireAt, 2).r == List(
      task.copy(status = Status.Acquired, updatedAt = acquireAt),
      task1.copy(status = Status.Acquired, updatedAt = acquireAt),
    ))
  }

  it should "not acquire same tasks twice" in new ctx {
    scheduler.schedule(ScheduledTaskFixture.someTask()).r

    scheduler.acquireBatch(Instant.now(), 100).r should have length (1)
    scheduler.acquireBatch(Instant.now(), 100).r should have length (0)
  }

  it should "throw an error if done is called on non created task" in new ctx {
    assertThrows[TaskIsNotInExpectedStatusException] {
      scheduler.done(ScheduledTaskFixture.someTask().id, Instant.now()).r
    }
  }

  it should "throw an error if done is called on succeeded task" in new ctx {
    assertThrows[TaskIsNotInExpectedStatusException] {
      val id = scheduler.schedule(ScheduledTaskFixture.someTask()).r.id
      scheduler.done(id, Instant.now()).r
      scheduler.done(id, Instant.now()).r
    }
  }

  it should "mark as failed acquired task " in new ctx {
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    scheduler.acquireBatch(Instant.now(), 1).r

    val failedAt = Instant.now()
    assert(scheduler.failed(task.id, failedAt, None, None).r == task.copy(status = Status.Failed, updatedAt = failedAt))

    val logs = scheduler.logs(task.id).r

    logs should have size (3)

    logs.last should beLike(task.id, Status.Failed, failedAt)
  }

  it should "update tasks payload" in new ctx{
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    scheduler.acquireBatch(Instant.now(), 1).r
    val failedAt = Instant.now()
    val updatedPayload = Json.obj("test" -> "test2")

    val failedTask = scheduler.failed(task.id, failedAt, None, Some(updatedPayload)).r
    assert(failedTask.payload == updatedPayload)

    val logs = scheduler.logs(task.id).r

    logs should have size (3)

    assert(logs.last.payload == updatedPayload)
  }


  trait ctx {
    val scheduler: ScheduledTaskService[IO] = new DoobieScheduledTaskService[IO](xa("scheduled_tasks", "scheduled_tasks_change_log"), new Observer[IO] {
      override def occurred(event: Event): IO[Unit] = IO.println(s"event occurred ${event}")
    })
  }

}
