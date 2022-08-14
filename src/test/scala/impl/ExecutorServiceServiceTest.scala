package rozklad
package impl

import api.Event._
import api._
import test.Shortcuts
import test.fixture.ScheduledTaskFixture
import test.matcher.ScheduledTaskLogMatchers
import test.mock.RecordingObserver

import cats.effect.{IO, Sync}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.flatspec.AnyFlatSpec
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration._

class ExecutorServiceServiceTest extends AnyFlatSpec with ScheduledTaskLogMatchers with MockFactory {

  "ExecutorService" should "report failing" in new ctx {
    self =>
    val e = new RuntimeException("test")
    (tasks.acquireBatch _).expects(*, *).returning(IO.raiseError(e)).once()
    val es = createExecutor
    eventually {
      assert {
        observer.events.exists {
          case ExecutorFailedDuringAcquiringBatch(_, self.e) => true
          case _ => false
        }
      }
    }

    es.stop.r
  }

  it should "report sleep event" in new ctx {
    (tasks.acquireBatch _).expects(*, *).returning(IO(List())).once()

    val es = createExecutor
    eventually {
      assert {
        observer.events.exists {
          case NothingWasAcquiredLastTimeGoingToSleep(_) => true
          case _ => false
        }
      }
    }

    es.stop.r
  }

  it should "report successful execution" in new ctx {
    (tasks.acquireBatch _).expects(*, *).returning(IO(List(task)))
    (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Succeeded.empty))
    val es = createExecutor
    eventually {
      assert {
        observer.events.exists {
          case ExecutionSucceeded(task.id, _, None) => true
          case _ => false
        }
      }
    }

    es.stop.r
  }

  it should "report failed execution" in new ctx {
    self =>
    (tasks.acquireBatch _).expects(*, *).returning(IO(List(task)))
    val e = new RuntimeException("error")
    val reason: FailedReason.Exception.type = FailedReason.Exception
    val payload: JsObject = Json.obj("Hui" -> "no")
    (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Failed(Option(reason), Some(payload))))

    val es = createExecutor

    eventually {
      assert {
        observer.events.exists {
          case ExecutionFailed(task.id, _, Some(self.reason), Some(self.payload)) => true
          case _ => false
        }
      }
    }

  }

  it should "report errored execution" in new ctx {
    self =>
    (tasks.acquireBatch _).expects(*, *).returning(IO(List(task)))
    (tasks.failed _).expects(*, *, *, *).returning(IO(task))
    val e = new RuntimeException("error")
    (executor.execute _).expects(*).returning(IO.raiseError(e))

    val es = createExecutor

    eventually {
      assert {
        observer.events.exists {
          case ExecutionErrored(task.id, _, self.e) => true
          case _ => false
        }
      }
    }

    es.stop.r

  }

  it should "end till current acquire and processing is completed" in new ctx {
    (tasks.acquireBatch _).expects(*, *).returning(IO.sleep(5.seconds).uncancelable *> IO(List(task)).uncancelable)
    (executor.execute _).expects(*).returning(IO.sleep(5.seconds).uncancelable *> IO(ScheduledTaskOutcome.Succeeded.empty))
    val es = createExecutor
    val from = System.currentTimeMillis()
    es.stop.r
    val to = System.currentTimeMillis()
    assert(to.millis - from.millis < 11.seconds.toMillis.millis)

  }

  trait ctx extends Shortcuts {
    val task: ScheduledTask = ScheduledTaskFixture.someTask()

    val observer: RecordingObserver = RecordingObserver()
    val tasks = mock[ScheduledTaskService[IO]]
    val executor = mock[Executor[IO]]

    def giveSucceededExecution = {
      (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Succeeded.empty))
    }

    def createExecutor: ExecutorService[IO] =
      ExecutorService.start(tasks, executor, observer, 1.second).r
  }

}
