package rozklad
package test.matcher

import org.scalatest.matchers.should.Matchers.have
import org.scalatest.matchers.{MatchResult, Matcher}
import rozklad.api.{Id, ScheduledTask, Status}
import rozklad.db.ScheduledTaskLog

import java.time.Instant
import scala.reflect.ClassTag

trait ScheduledTaskLogMatchers {


  def sameTaskId(id: Id[ScheduledTask]) = genericMatcher[ScheduledTaskLog, Id[ScheduledTask]](_.taskId, id, "task id")

  def sameStatus(status: Status) = genericMatcher[ScheduledTaskLog, Status](_.status, status, "status")

  def sameCreatedAt(createdAt: Instant) = genericMatcher[ScheduledTaskLog, Instant](_.createdAt, createdAt, "createdAt")

  def genericMatcher[A : ClassTag, B](extractor: A => B, etalon: B, field: String) = Matcher[A] {
    (left: A) =>
      MatchResult(
        extractor(left) == etalon,
        s"expected ${field} is ${etalon} but actual ${field} is ${extractor(left)} in ${left}",
        s"${field} is ok"
      )
  }


  def beLike(id: Id[ScheduledTask], status: Status, createdAt: Instant): Matcher[ScheduledTaskLog] = sameTaskId(id) and sameStatus(status) and sameCreatedAt(createdAt)

}
