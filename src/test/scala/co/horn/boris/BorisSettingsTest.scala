/*
 * Copyright © 2015 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}

import scala.util.Try

class BorisSettingsTest extends FunSpec with ScalaFutures with Matchers {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))
  val systemConfig = system.settings.config.getConfig("horn.boris")

  describe("BorisSettings") {
    describe("have some requirements for initial parameters.") {

      it("timeouts must be greater than zero.") {
        var config = ConfigFactory.parseString("request-timeout = 0s").withFallback(systemConfig)
        var settings = Try(BorisSettings(config))
        settings.failed.get shouldBe an[IllegalArgumentException]

        config = ConfigFactory.parseString("materialize-timeout = 0s").withFallback(systemConfig)
        settings = Try(BorisSettings(config))
        settings.failed.get shouldBe an[IllegalArgumentException]
      }

      it("queue bufferSize must be greater than zero") {
        val config = ConfigFactory.parseString("bufferSize = 0").withFallback(systemConfig)
        val settings = Try(BorisSettings(config))
        settings.failed.get shouldBe an[IllegalArgumentException]
      }
    }
  }
}
