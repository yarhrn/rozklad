package rozklad
package test.implicits

import api.{ScheduledTask, ScheduledTaskService}

object RichScheduledTaskService{

  implicit class RichScheduledTaskService[F[_]](scheduledTaskService: ScheduledTaskService[F]) {
    def schedule(task: ScheduledTask) =
      scheduledTaskService
        .schedule(task.id, task.triggerAt, task.scheduledAt, task.payload)
  }

}

