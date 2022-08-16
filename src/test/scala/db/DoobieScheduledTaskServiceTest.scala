package rozklad
package db

import api.{Event, Observer, ScheduledTask, ScheduledTaskService, Status, TaskIsNotInExpectedStatusException}
import test.EmbeddedPosrtesqlDBEnv
import test.fixture.ScheduledTaskFixture
import test.matcher.ScheduledTaskLogMatchers

import cats.effect.IO
import doobie.Transactor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import play.api.libs.json.Json
import rozklad.api.Event.{ScheduledTaskDone, ScheduledTaskFailed, ScheduledTasksAcquired}
import rozklad.test.mock.RecordingObserver
import rozklad.test.implicits.RichScheduledTaskService._

import java.time.Instant

class DoobieScheduledTaskServiceTest extends AnyFlatSpec with EmbeddedPosrtesqlDBEnv with ScheduledTaskLogMatchers {

  "DoobieScheduler" should "handle happy path" in new ctx {
    val task = ScheduledTaskFixture.someTask()
    scheduler.schedule(task).r

    val acquiredAt = Instant.now()
    val acquiredTask: ScheduledTask =
      task.copy(status = Status.Acquired, updatedAt = acquiredAt)
    assert(tasks.acquireBatch(acquiredAt, 10).r == List(acquiredTask))
    assert(observer.take == ScheduledTasksAcquired(List(acquiredTask)))
    observer.clean

    val succeededAt = Instant.now()
    val succeededTask: ScheduledTask =
      task.copy(status = Status.Succeeded, updatedAt = succeededAt)
    assert(tasks.done(task.id, succeededAt, None).r == succeededTask)
    assert(observer.take == ScheduledTaskDone(succeededTask))
    observer.clean

    val logs = tasks.logs(task.id).r

    logs should have length 3

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
    assert(
      tasks.acquireBatch(acquireAt, 2).r == List(
        task.copy(status = Status.Acquired, updatedAt = acquireAt),
        task1.copy(status = Status.Acquired, updatedAt = acquireAt)
      ))
  }

  it should "not acquire same tasks twice" in new ctx {
    scheduler.schedule(ScheduledTaskFixture.someTask()).r

    tasks.acquireBatch(Instant.now(), 100).r should have length 1
    tasks.acquireBatch(Instant.now(), 100).r should have length 0
  }

  it should "throw an error if done is called on non created task" in new ctx {
    assertThrows[TaskIsNotInExpectedStatusException] {
      tasks.done(ScheduledTaskFixture.someTask().id, Instant.now(), None).r
    }
  }

  it should "throw an error if done is called on succeeded task" in new ctx {
    assertThrows[TaskIsNotInExpectedStatusException] {
      val id = scheduler.schedule(ScheduledTaskFixture.someTask()).r.id
      tasks.done(id, Instant.now(), None).r
      tasks.done(id, Instant.now(), None).r
    }
  }

  it should "mark as failed acquired task " in new ctx {
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    tasks.acquireBatch(Instant.now(), 1).r

    val failedAt = Instant.now()
    assert(tasks.failed(task.id, failedAt, None, None).r == task.copy(status = Status.Failed, updatedAt = failedAt))

    val logs = tasks.logs(task.id).r

    logs should have size 3

    logs.last should beLike(task.id, Status.Failed, failedAt)
  }

  it should "update tasks payload on failed" in new ctx {
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    tasks.acquireBatch(Instant.now(), 1).r
    observer.clean
    val failedAt = Instant.now()
    val updatedPayload = Json.obj("test" -> "test2")

    val failedTask = tasks.failed(task.id, failedAt, None, Some(updatedPayload)).r
    assert(failedTask.payload == updatedPayload)
    assert(observer.take == ScheduledTaskFailed(failedTask))

    val logs = tasks.logs(task.id).r

    logs should have size 3

    assert(logs.last.payload == updatedPayload)
  }

  it should "update tasks payload on done" in new ctx {
    val task = scheduler.schedule(ScheduledTaskFixture.someTask()).r
    tasks.acquireBatch(Instant.now(), 1).r
    observer.clean
    val succeededAt = Instant.now()
    val updatedPayload = Json.obj("test" -> "test2")

    val succeededTask = tasks.done(task.id, succeededAt, Some(updatedPayload)).r
    assert(succeededTask.payload == updatedPayload)
    assert(observer.take == ScheduledTaskDone(succeededTask))

    val logs = tasks.logs(task.id).r

    logs should have size 3

    assert(logs.last.payload == updatedPayload)
  }

  trait ctx {
    val observer: RecordingObserver = RecordingObserver()
    val xaa: Transactor[IO] = xa("scheduled_tasks", "scheduled_tasks_change_log")
    val tasks: ScheduledTaskService[IO] = new DoobieScheduledTaskService[IO](xaa, observer)

    val scheduler = new DoobieTaskScheduler[IO](xaa)

  }

}
