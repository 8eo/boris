/*
 * Copyright © 2015 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}

import scala.util.Try

class CommonTests extends FunSpec with ScalaFutures with Matchers {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  describe("The RestClient") {
    describe("have some requirements for initial parameters.") {

      it("the uri list cannot be empty") {
        val pool = Try(PooledMultiServerRequest(Seq.empty, ConnectionPoolSettings(system)))
        pool.failed.get shouldBe an[IllegalArgumentException]
      }

      it("timeouts must be greater than zero.") {
        var config = ConfigFactory.parseString("horn.boris.request-timeout = 0s").withFallback(system.settings.config)
        var pool = Try(PooledMultiServerRequest(Seq.empty, ConnectionPoolSettings(system), config))
        pool.failed.get shouldBe an[IllegalArgumentException]

        config = ConfigFactory.parseString("horn.boris.materialize-timeout = 0s").withFallback(system.settings.config)
        pool = Try(PooledMultiServerRequest(Seq.empty, ConnectionPoolSettings(system), config))
        pool.failed.get shouldBe an[IllegalArgumentException]
      }

      it("request timeout must be larger than timeout for entity materialization") {
        val config = ConfigFactory.parseString("""
            |horn.boris{
            |  request-timeout = 4s
            |  materialize-timeout = 5s
            |}
          """.stripMargin).withFallback(system.settings.config)
        val pool = Try(PooledMultiServerRequest(Seq.empty, ConnectionPoolSettings(system), config))
        pool.failed.get shouldBe an[IllegalArgumentException]
      }

      it("queue bufferSize must be greater than zero") {
        val config = ConfigFactory.parseString("horn.boris.bufferSize = 0").withFallback(system.settings.config)
        val pool = Try(PooledMultiServerRequest(Seq.empty, ConnectionPoolSettings(system), config))
        pool.failed.get shouldBe an[IllegalArgumentException]
      }
    }
  }
}
