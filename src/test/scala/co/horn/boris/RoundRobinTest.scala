/*
 * Copyright © 2015 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ Uri, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures

import org.scalatest.time.{ Seconds, Span }
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{ Matchers, BeforeAndAfter, FunSpec }

import scala.concurrent.Future

class RoundRobinTest extends FunSpec with BeforeAndAfter with ScalaFutures with Matchers {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(timeout = Span(10, Seconds))

  // Very simple routing that just returns the current server instance
  def route(instance: String) = get {
    path("bumble") {
      complete(StatusCodes.OK, instance)
    }
  }
  var servers = Seq[ServerBinding]()
  val instances = 0 until 5
  val uri = instances.map(i ⇒ Uri(s"http://localhost:${10100 + i}"))

  before {
    servers = instances.map(i ⇒ Http().bindAndHandle(route(i.toString), "localhost", 10100 + i).futureValue)
  }

  after {
    servers.foreach(_.unbind.futureValue)
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
      servers(1).unbind.futureValue
      val pool = RestClient(uri, ConnectionPoolSettings(system))
      val ret = (0 until 20).map { i ⇒
        pool.exec(Get("/bumble")).flatMap(f ⇒ Unmarshal(f.entity).to[String]).futureValue
      }
      (0 until 5).foreach(i ⇒ ret.count(_ == i.toString) shouldBe 4)
    }

  }

}
