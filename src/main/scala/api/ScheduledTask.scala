package rozklad
package api

import enumeratum.values.{IntEnum, IntEnumEntry}
import play.api.libs.json.{JsNull, JsValue}

import java.time.Instant
import java.util.UUID

case class ScheduledTask(
    id: Id[ScheduledTask],
    scheduledAt: Instant,
    triggerAt: Instant,
    status: Status,
    updatedAt: Instant,
    failedReason: Option[FailedReason],
    payload: JsValue = JsNull)

case class Id[A](id: UUID) {
  override def toString: String = id.toString
}

object Id {
  def random[A] = Id[A](UUID.randomUUID())
}

sealed abstract class Status(val value: Int) extends IntEnumEntry

object Status extends IntEnum[Status] {

  case object Created extends Status(0)

  case object Acquired extends Status(1)

  case object Succeeded extends Status(2)

  case object Failed extends Status(3)

  override def values: IndexedSeq[Status] = findValues
}

sealed abstract class FailedReason(val value: Int) extends IntEnumEntry

object FailedReason extends IntEnum[Status] {

  case object Expired extends FailedReason(0)

  case object Exception extends FailedReason(1)

  override def values: IndexedSeq[Status] = findValues
}
