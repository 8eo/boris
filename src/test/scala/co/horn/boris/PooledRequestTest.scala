/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global

class PooledRequestTest() extends AnyFunSpec with BeforeAndAfterEach with ScalaFutures with Matchers with Eventually {

  implicit val system       = ActorSystem("Test")
  implicit val materializer = Materializer(system)
  implicit val patience     = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  // Very simple routing that just returns the current server instance
  def route(instance: Int): Route =
    get {
      path("bumble") {
        complete(StatusCodes.OK, instance.toString)
      } ~
        path("slow") {
          if (instance == 1) Thread.sleep(1000)
          complete(StatusCodes.OK, instance.toString)
        } ~
        path("slow" / "abit") {
          Thread.sleep(50)
          complete(StatusCodes.OK, instance.toString)
        }
    }

  private var server: ServerBinding = _
  private val uri                   = Uri(s"http://localhost:${10100}")

  override def beforeEach {
    Http(system).shutdownAllConnectionPools() // Terminate all pools so the servers can actually shut down
    server = Http().bindAndHandle(route(1234), "localhost", 10100).futureValue
  }

  override def afterEach {
    server.unbind.futureValue
  }

  describe("Pooled single server requests") {

    it("exec calls to the specified URI") {
      val pool = PooledSingleServerRequest(uri, ConnectionPoolSettings(system), BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234")
    }

    it("execStrict calls to the specified URI") {
      val pool = PooledSingleServerRequest(uri, ConnectionPoolSettings(system), BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.execStrict(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234")
    }

    it("execDrop calls to the specified URI") {
      val pool = PooledSingleServerRequest(uri, ConnectionPoolSettings(system), BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.execDrop(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234") // Responses arrive anyway because they are so small
    }
  }
}
