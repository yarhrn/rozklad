package rozklad
package test

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.Transactor
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.testcontainers.utility.DockerImageName

import java.sql.DriverManager
import doobie.implicits._

trait EmbeddedPosrtesqlDBEnv extends BeforeAndAfterAll with Shortcuts {
  self: Suite =>

  override def beforeAll(): Unit = {
    println("here1")
    postgres.start()
    println(s"url: ${postgres.jdbcUrl}, username: ${postgres.username}, password: ${postgres.password}")
    refreshScheme()
  }

  def refreshScheme() = {
    Thread.sleep(5000)
    val connection =
      DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
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

    dbs.foreach { db => doobie.Fragment.const(s"delete from ${db}").update.run.transact(inner).r }

    inner
  }

  var postgres: PostgreSQLContainer = new PostgreSQLContainer(Some(DockerImageName.parse("postgres:11")))

}
