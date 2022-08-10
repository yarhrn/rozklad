package rozklad
package impl

import test.EmbeddedPosrtesqlDBEnv
import test.matcher.ScheduledTaskLogMatchers

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import rozklad.api.{Event, Executor, NoScheduledTasksWereAcquired, Observer, ScheduledTask, ScheduledTaskDone, ScheduledTaskService, ScheduledTasksAcquired}
import rozklad.db.DoobieScheduledTaskService
import rozklad.test.fixture.ScheduledTaskFixture

class ExecutorServiceTest extends AnyFlatSpec with EmbeddedPosrtesqlDBEnv with ScheduledTaskLogMatchers{

  "ExecutorService" should "dsf" in new ctx{

    while(true){
      scheduler.schedule(ScheduledTaskFixture.someTask()).r
      println("scheduled")
      Thread.sleep(500)
    }

  }


  trait ctx {
    val scheduler: ScheduledTaskService[IO] = new DoobieScheduledTaskService[IO](xa("scheduled_tasks", "scheduled_tasks_change_log"), new Observer[IO] {
      override def occurred(event: Event): IO[Unit] = event match {
        case ScheduledTasksAcquired(tasks) => IO.println(s"acquired batch ${tasks.length}")
        case _ => IO.unit
      }
    })
    def foo(task: ScheduledTask): IO[Unit] = IO.blocking(println(task))

    new Thread(() => Executor.start(scheduler, foo).r).start()
  }

}
