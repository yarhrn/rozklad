package rozklad

import api.{Id, Status}

import doobie.util.meta.Meta
import org.postgresql.util.PGobject
import play.api.libs.json.{JsValue, Json}

import java.util.UUID

package object db {
  implicit def idMeta[A]: Meta[Id[A]] = Meta[String].imap(s => Id[A](UUID.fromString(s)))(id => id.id.toString)

  implicit def statusMeta[A <: Status]: Meta[A] = Meta[Int].imap(k => Status.withValue(k).asInstanceOf[A])(_.value)

  implicit val JsonMeta: Meta[JsValue] =
    Meta.Advanced.other[PGobject]("json").timap[JsValue](
      a => Json.parse(a.getValue))(
      a => {
        val o = new PGobject
        o.setType("json")
        o.setValue(Json.stringify(Json.toJson(a)))
        o
      }
    )

}
