package rozklad
package api

trait Observer[F[_]] {

  def occurred(event: Event): F[Unit]

}

sealed trait Event

case class ScheduledTasksAcquired(tasks: List[ScheduledTask]) extends Event
case class ScheduledTaskDone(task: ScheduledTask) extends Event
case object NoScheduledTasksWereAcquired extends Event