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
import rozklad.test.implicits.RichScheduledTaskService._

import scala.concurrent.duration._
import scala.reflect.ClassTag

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
    s =>
    (tasks.acquireBatch _).expects(*, *).returning(IO(List(task)))
    (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Succeeded.empty))
    (tasks.done _).expects(*, *, *).returning(IO(task))

    val es = createExecutor
    eventually {
      val event = extractEvent[ExecutionSucceeded]
      assert(event.task == s.task)
      assert(event.payload.isEmpty)
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
    (tasks.failed _).expects(*, *, *, *).returning(IO(task))

    val es = createExecutor

    eventually {
      val event = extractEvent[ExecutionFailed]

      assert(event.payload.contains(payload))
      assert(task == event.task)
      assert(event.reason.contains(reason))
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
      val event = extractEvent[ExecutionErrored]
      assert(task == event.task)
      assert(e == event.exception)
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

  it should "propagate error during handling execution result" in new ctx {
    s =>
    val ex = new RuntimeException("dsfsdf")

    (tasks.acquireBatch _).expects(*, *).returning(IO(List(task)))
    (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Succeeded.empty))
    (tasks.done _).expects(*, *, *).throws(ex)

    val es = createExecutor
    eventually {
      observer.events.exists {
        case ErrorDuringHandlingExecutionResult(s.task, _, s.ex) => true
        case _ => false
      }
    }

    es.stop.r

  }

  trait ctx extends Shortcuts {
    def extractEvent[A: ClassTag] = {
      observer.events.find(implicitly[ClassTag[A]].runtimeClass.isInstance).getOrElse(throw new RuntimeException("not found")).asInstanceOf[A]
    }

    val task: ScheduledTask = ScheduledTaskFixture.someTask()

    val observer: RecordingObserver = RecordingObserver()
    val tasks = mock[ScheduledTaskService[IO]]
    val executor = mock[ScheduledTaskExecutor[IO]]

    def giveSucceededExecution = {
      (executor.execute _).expects(*).returning(IO(ScheduledTaskOutcome.Succeeded.empty))
    }

    def createExecutor: ExecutorService[IO] =
      ExecutorService.start(tasks, executor, observer, 1.second).r
  }

}
