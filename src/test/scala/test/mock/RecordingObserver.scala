package rozklad
package test.mock

import api.{Event, Observer}

import cats.effect.{IO, Ref}
import rozklad.test.Shortcuts

class RecordingObserver(ref: Ref[IO, List[Event]]) extends Observer[IO] with Shortcuts {

  override def occurred(event: Event): IO[Unit] = ref.update(_.appended(event))

  def events = ref.get.r

  def clean = ref.set(List.empty).r

  def take = ref.get.map(_.head).r

}

object RecordingObserver extends Shortcuts {
  def apply(): RecordingObserver = {
    Ref.of[IO, List[Event]](List.empty[Event]).map(ref => new RecordingObserver(ref)).r
  }
}
