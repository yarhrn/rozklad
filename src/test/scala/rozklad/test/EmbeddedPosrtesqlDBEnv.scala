package rozklad.test

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.Transactor
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager}
import doobie.implicits._

import scala.util.Try

trait EmbeddedPosrtesqlDBEnv extends BeforeAndAfterAll with Shortcuts {
  self: Suite =>

  override def beforeAll(): Unit = {
    postgres.start()
    refreshScheme()
  }

  def refreshScheme(): Unit = {
    var attempt: Int = 1
    var connectionOption: Option[Connection] = None
    while (attempt < 10) {
      Thread.sleep(2000)
      connectionOption = Try(DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)).toOption
      if (connectionOption.isDefined) {
        attempt = 100
      } else {
        attempt = attempt + 1
      }
    }

    val connection = connectionOption.getOrElse(throw new RuntimeException("cant obtain connection to test DB"))

    val statement = connection.createStatement()
    val source = io.Source.fromResource("./scheme.sql")
    statement.execute(source.getLines().mkString("\n"))
    source.close()
    connection.close()
  }

  override def afterAll(): Unit = {
    postgres.stop()
  }

  def xa(dbs: String*) = {
    val inner: Transactor[IO] = Transactor.fromDriverManager(
      "org.postgresql.Driver",
      postgres.jdbcUrl,
      postgres.username,
      postgres.password
    )

    dbs.foreach { db => doobie.Fragment.const(s"delete from $db").update.run.transact(inner).r }

    inner
  }

  var postgres: PostgreSQLContainer = new PostgreSQLContainer(Some(DockerImageName.parse("postgres:15")))

}
