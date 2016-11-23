/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import akka.http.scaladsl.server.Directives._

class PooledRequestTest() extends FunSpec with BeforeAndAfterEach with ScalaFutures with Matchers with Eventually {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

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

  describe("Pooled single server requests") {
    it("execute calls to the specified URI"){

      pending
    }
  }
}
