/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.ExecutionContext.Implicits.global

class SingleRequestTest() extends FunSpec with BeforeAndAfterEach with ScalaFutures with Matchers with Eventually {

  implicit val system: ActorSystem = ActorSystem("Test")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val patience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  // Very simple routing that just returns the current server instance
  def route(instance: Int): Route = get {
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
      } ~
      path("painfully_slow") {
        Thread.sleep(10000)
        complete(StatusCodes.OK, instance.toString)
      }
  }

  private var server: ServerBinding = _
  private val uri = Uri(s"http://localhost:${10100}")

  override def beforeEach {
    Http(system).shutdownAllConnectionPools() // Terminate all pools so the servers can actually shut down
    server = Http().bindAndHandle(route(1234), "localhost", 10100).futureValue
  }

  override def afterEach {
    server.unbind.futureValue
  }

  describe("Single server requests") {

    it("exec calls to the specified URI") {
      val pool = SingleServerRequest(uri, BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234")
    }

    it("execStrict calls to the specified URI") {
      val pool = SingleServerRequest(uri, BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.execStrict(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234")
    }

    it("execDrop calls to the specified URI") {
      val pool = SingleServerRequest(uri, BorisSettings(system))
      val ret = (0 until 20).map { _ ⇒
        pool.execDrop(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret shouldBe List.fill(20)("1234") // Responses arrive anyway because they are so small
    }

    it("handles calls that time out on the server") {
      val pool = SingleServerRequest(uri, BorisSettings(system))
      pool.execDrop(Get("/painfully_slow")).failed.futureValue shouldBe a[TimeoutException]
      pool.execDrop(Get("/painfully_slow")).failed.futureValue shouldBe a[TimeoutException]
      pool.exec(Get("/painfully_slow")).failed.futureValue shouldBe a[TimeoutException]
    }

    it("survives the server being killed") {
      val pool = SingleServerRequest(uri, BorisSettings(system))
      val req = pool.execDrop(Get("/painfully_slow"))
      server.unbind.futureValue
      req.failed.futureValue shouldBe a[TimeoutException]
    }
  }
}
