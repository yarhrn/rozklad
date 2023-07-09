package rozklad.db

import cats.free.Free
import cats.implicits._
import doobie.Update0
import doobie.free.connection
import doobie.postgres.implicits._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments
import play.api.libs.json.JsValue
import rozklad.api.{FailedReason, Id, ScheduledTask, Status}

import java.time.Instant

class DoobieScheduledTaskRepository(table: String) extends ScheduledTaskRepository {

  private val ScheduledTaskColumns = List("id", "scheduled_at", "trigger_at", "status", "updated_at", "failed_reason", "payload")

  implicit class Update0Rich(update: Update0) {

    def returningScheduledTask: doobie.ConnectionIO[List[ScheduledTask]] = {
      update.withGeneratedKeys[ScheduledTask](ScheduledTaskColumns: _*).compile.toList
    }
  }

  def schedule(descriptor: ScheduledTask): doobie.ConnectionIO[Unit] =
    (fr"""
        insert into""" ++ Fragment.const(table) ++
      fr"""(id, scheduled_at, trigger_at, status, updated_at, payload)
        values (${descriptor.id}, ${descriptor.scheduledAt}, ${descriptor.triggerAt}, ${descriptor.status}, ${descriptor.updatedAt}, ${descriptor.payload})
        on conflict do nothing
      """).update.run.flatMap { result => if (result == 0) InsertDuplicate().raiseError[ConnectionIO, Unit] else ().pure[ConnectionIO] }

  def acquireBatch(now: Instant, limit: Int): doobie.ConnectionIO[List[ScheduledTask]] = {
    (fr"""
         with ready_to_acquire as (
             select id as ready_to_acquire_id
                from """ ++ Fragment.const(table) ++
      fr"""
                where (status = ${Status.Created} and trigger_at < $now) or
                      (status = ${Status.Rescheduled} and trigger_at < $now)
                order by scheduled_at
                FOR UPDATE SKIP LOCKED
                limit $limit
         )
         update """ ++ Fragment.const(table) ++
      fr"""
         set status = ${Status.Acquired}, updated_at = $now
         from ready_to_acquire
         where """ ++ Fragment.const(table) ++
      fr""".id = ready_to_acquire_id
       """).update.returningScheduledTask
  }

  def delete(id: Id[ScheduledTask]): doobie.ConnectionIO[Option[ScheduledTask]] = {
    (fr"""
         delete from """ ++ Fragment.const(table) ++
      fr"""
         where id = $id
       """).update.returningScheduledTask.map(_.headOption)
  }

  def select(id: Id[ScheduledTask]): doobie.ConnectionIO[Option[ScheduledTask]] = {
    fr"select " ++ Fragment.const(ScheduledTaskColumns.mkString(",")) ++
      fr" from " ++ Fragment.const(table) ++ fr""" where id = $id"""
  }.query[ScheduledTask].option

  def done(id: Id[ScheduledTask], now: Instant, payload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      id = id,
      now = now,
      failedReason = None,
      payload = payload,
      fromStatus = List(Status.Acquired),
      toStatus = Status.Succeeded,
      None,
      None
    )
  }

  def reschedule(now: Instant, descriptor: ScheduledTask): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      descriptor.id,
      now,
      None,
      Option(descriptor.payload),
      List(
        Status.Created,
        Status.Rescheduled,
        Status.Failed,
        Status.Succeeded
      ),
      Status.Rescheduled,
      Some(descriptor.triggerAt),
      Some(descriptor.scheduledAt)
    )
  }

  def failed(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      updatedPayload: Option[JsValue]): doobie.ConnectionIO[List[ScheduledTask]] = {
    transition(
      id = id,
      now = now,
      failedReason = failedReason,
      payload = updatedPayload,
      fromStatus = List(Status.Acquired),
      toStatus = Status.Failed,
      None,
      None
    )
  }

  private def transition(
      id: Id[ScheduledTask],
      now: Instant,
      failedReason: Option[FailedReason],
      payload: Option[JsValue],
      fromStatus: List[Status],
      toStatus: Status,
      triggerAt: Option[Instant],
      scheduledAt: Option[Instant]): doobie.ConnectionIO[List[ScheduledTask]] = {

    val update = fr"""update """ ++ Fragment.const(table)

    val returning = fr"returning " ++ Fragment.const(ScheduledTaskColumns.mkString(","))

    val where = fragments.whereAnd(
      fr"id = $id ",
      fragments.or(fromStatus.map(status => fr""" status = $status """): _*)
    )

    val set = fragments.setOpt(
      Option(fr"status = $toStatus"),
      Option(fr"updated_at = $now"),
      triggerAt.map(triggerAt => fr"trigger_at = $triggerAt"),
      scheduledAt.map(scheduledAt => fr"scheduled_at = $scheduledAt"),
      failedReason.map(failedReason => fr"failed_reason = $failedReason"),
      payload.map(payload => fr"payload = $payload ")
    )

    val query = update ++
      set ++
      where ++
      returning

    query.query[ScheduledTask].to[List]
  }

}