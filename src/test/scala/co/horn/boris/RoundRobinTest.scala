/*
 * Copyright © 2015 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RoundRobinTest extends FunSpec with BeforeAndAfterEach with ScalaFutures with Matchers with Eventually {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  // Very simple routing that just returns the current server instance
  def route(instance: Int) = get {
    path("bumble") {
      complete(StatusCodes.OK, instance.toString)
    } ~
    path("slow") {
      if (instance == 1) Thread.sleep(1000)
      complete(StatusCodes.OK, instance.toString)
    }
  }
  var servers = Seq[ServerBinding]()
  val instances = 0 until 5
  val uri = instances.map(i ⇒ Uri(s"http://localhost:${10100 + i}"))

  override def beforeEach {
    Http(system).shutdownAllConnectionPools() // Terminate all pools so the servers can actually shut down
    servers = instances.map(i ⇒ Http().bindAndHandle(route(i), "localhost", 10100 + i).futureValue)
  }

  override def afterEach {
    Future.sequence(servers.map(_.unbind)).futureValue
  }

  describe("The round robin scheduler") {

    it("visits the servers in turn") {
      val pool = RestClient(uri, ConnectionPoolSettings(system))
      val ret = (0 until 20).map { i ⇒
        pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      (0 until 5).foreach(i ⇒ ret.count(_ == i.toString) shouldBe 4)
    }

    it("catches a failed server") {
      servers(1).unbind.futureValue // This is not reliably shutting down this server
      val pool = RestClient(uri, ConnectionPoolSettings(system))
      val ret = (0 until 20).map { i ⇒
        pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret.toSet should contain only("0", "2", "3", "4")
    }

    it("fails if no servers are able to serve the request") {
      val pool = RestClient(Seq(Uri("http://localhost:10123"), Uri("http://localhost:10124"), Uri("http://localhost:10125")), ConnectionPoolSettings(system))
      pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).failed.futureValue shouldBe NoServersResponded
    }

    it("handles servers that time out") {
      val pool = RestClient(uri, ConnectionPoolSettings(system), 0.5 seconds)
      val ret = (0 until 20).map { i ⇒
        pool.exec(Get("/slow")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      ret.toSet should contain only("0", "2", "3", "4") // Server "1" is slow
    }
  }
}
