/*
 * Copyright © 2015 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}
import scala.concurrent.duration._

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
    it("can be constructed from plain string, config object or system") {
      val str =
        """
          |name = "boris_rest_client"
          |bufferSize = 100
          |overflowStrategy = "dropNew"
          |request-timeout = 10s
          |materialize-timeout = 8s
        """.stripMargin

      BorisSettings(str)
      BorisSettings(ConfigFactory.parseString(str))
      BorisSettings(system)
    }

    it("settings cannot be overridden using dedicated methods") {
      val str =
        """
          |name = "boris_rest_client"
          |bufferSize = 100
          |overflowStrategy = "dropNew"
          |request-timeout = 10s
          |materialize-timeout = 8s
        """.stripMargin
      val conf = BorisSettings(str)
      conf.withName("new name").name should be("new name")
      conf.withBufferSize(1).bufferSize should be(1)
      conf.withOverflowStrategy(OverflowStrategy.dropHead).overflowStrategy should be(OverflowStrategy.dropHead)
      conf.withRequestTimeout(5 seconds).requestTimeout should be(5 seconds)
      conf.withStrictMaterializeTimeout(3 seconds).strictMaterializeTimeout should be(3 seconds)
    }

    it("parse string that that corresponds to OverflowStrategy") {
      QueueOverflowStrategy("dropNew") should be(OverflowStrategy.dropNew)
      QueueOverflowStrategy("dropHead") should be(OverflowStrategy.dropHead)
      QueueOverflowStrategy("dropTail") should be(OverflowStrategy.dropTail)
      QueueOverflowStrategy("dropBuffer") should be(OverflowStrategy.dropBuffer)
      QueueOverflowStrategy("backpressure") should be(OverflowStrategy.backpressure)
      QueueOverflowStrategy("fail") should be(OverflowStrategy.fail)
      QueueOverflowStrategy("wrong") should be(OverflowStrategy.dropNew)
    }
  }
}
