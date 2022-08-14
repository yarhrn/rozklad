package rozklad
package utils

import cats.{Eval, MonadError}
import cats.implicits._

trait SafeConstruct[F[_]] {

  def construct[A](fa: => F[A]): F[A]

}

object SafeConstruct {
  implicit def fromMonadError[F[_]](implicit ME: MonadError[F, Throwable]) =
    new SafeConstruct[F] {
      override def construct[A](fa: => F[A]): F[A] =
        ME.catchNonFatalEval(Eval.always(fa)).flatten
    }
}
