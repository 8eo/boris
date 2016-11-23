package co.horn.boris

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

/**
  * QueueOverflowStrategy is a parser for OverflowStrategy.
  */
object QueueOverflowStrategy {
  def apply(strategy: String): OverflowStrategy = strategy match {
    case "dropHead" ⇒ OverflowStrategy.dropHead
    case "dropTail" ⇒ OverflowStrategy.dropTail
    case "dropBuffer" ⇒ OverflowStrategy.dropBuffer
    case "dropNew" ⇒ OverflowStrategy.dropNew
    case "backpressure" ⇒ OverflowStrategy.backpressure
    case "fail" ⇒ OverflowStrategy.fail
    case fallback ⇒ OverflowStrategy.dropNew

  }
}

@throws(classOf[IllegalArgumentException])
case class BorisSettings(requestTimeout: FiniteDuration,
                         strictMaterializeTimeout: FiniteDuration,
                         bufferSize: Int,
                         overflowStrategy: OverflowStrategy) {

  require(requestTimeout > 0.seconds, "Request timeout must be larger than 0(zero)")
  require(strictMaterializeTimeout > 0.seconds, "Timeout for entity materialization must be larger than 0(zero)")
  require(requestTimeout > strictMaterializeTimeout,
    "Request timeout must be larger than timeout for entity materialization")
  require(bufferSize > 0, "Queue buffer size must be larger than 0(zero)")

  def withRequestTimeout(timeout: FiniteDuration): BorisSettings = this.copy(requestTimeout = timeout)

  def withStrictMaterializeTimeout(timeout: FiniteDuration): BorisSettings =
    this.copy(strictMaterializeTimeout = timeout)

  def withBufferSize(size: Int): BorisSettings = this.copy(bufferSize = size)

  def withOverflowStrategy(strategy: OverflowStrategy): BorisSettings = this.copy(overflowStrategy = strategy)
}

object BorisSettings {
  def apply(config: Config): BorisSettings = {
    val requestTimeout = config.getDuration("request-timeout", TimeUnit.MILLISECONDS).millis
    val strictMaterializeTimeout = config.getDuration("materialize-timeout", TimeUnit.MILLISECONDS).millis
    val bufferSize = config.getInt("bufferSize")
    val overflowStrategy = QueueOverflowStrategy(config.getString("overflowStrategy"))
    BorisSettings(requestTimeout, strictMaterializeTimeout, bufferSize, overflowStrategy)
  }

  def apply(configOverrides: String): BorisSettings = apply(ConfigFactory.parseString(configOverrides))

  def apply(system: ActorSystem): BorisSettings = apply(system.settings.config.getConfig("horn.boris"))
}
