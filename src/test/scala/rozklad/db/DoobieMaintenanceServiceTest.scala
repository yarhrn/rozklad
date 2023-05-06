package rozklad.db

import cats.effect.IO
import doobie.util.transactor.Transactor
import org.scalatest.flatspec.AnyFlatSpec
import rozklad.api.ScheduledTaskLog
import rozklad.test.EmbeddedPosrtesqlDBEnv
import rozklad.test.fixture.ScheduledTaskFixture
import rozklad.test.implicits.RichScheduledTaskService._
import rozklad.test.matcher.ScheduledTaskLogMatchers
import rozklad.test.mock.RecordingObserver

class DoobieMaintenanceServiceTest extends AnyFlatSpec with EmbeddedPosrtesqlDBEnv with ScheduledTaskLogMatchers {

  "DoobieMaintenanceService" should "remove stored scheduled task" in new ctx {
    scheduler.schedule(task).r
    scheduler.schedule(task1).r

    assert(scheduled.task(task.id).r.contains(task))
    val logs: List[ScheduledTaskLog] = scheduled.logs(task.id).compile.toList.r
    assert(logs.size == 1)

    val removed = maintenance.remove(task.id).r
    assert(removed.logs == logs)
    assert(removed.task.contains(task))

    assert(scheduled.task(task1.id).r.contains(task1))
    assert(scheduled.logs(task1.id).compile.toList.r.size == 1)
  }

  trait ctx {
    val task = ScheduledTaskFixture.someTask()
    val task1 = ScheduledTaskFixture.someTask()
    val observer: RecordingObserver = RecordingObserver()
    val xaa: Transactor[IO] = xa(DefaultScheduledTasksTableName, DefaultScheduledTasksLogsTableName)

    val scheduled = new DoobieScheduledTaskService[IO](xaa, observer)
    val scheduler = new DoobieTaskScheduler[IO](xaa, observer)
    val maintenance = new DoobieMaintenanceService[IO](xaa)

  }

}
