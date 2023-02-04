package rozklad.test.implicits

import rozklad.api.{ScheduledTask, TaskScheduler}

object RichScheduledTaskService {

  implicit class RichScheduledTaskService[F[_]](scheduledTaskService: TaskScheduler[F]) {
    def schedule(task: ScheduledTask) =
      scheduledTaskService.schedule(task.id, task.triggerAt, task.scheduledAt, task.payload)
  }

}
