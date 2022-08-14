package rozklad
package test

import cats.effect.IO

trait Shortcuts {
  import cats.effect.unsafe.implicits.global

  implicit class ZIORunnnn[A](pio: IO[A]) {
    def r = pio.unsafeRunSync()
  }

}
