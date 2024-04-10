package co.horn.boris

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import com.typesafe.config.{Config, ConfigFactory}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

/**
  * QueueOverflowStrategy is a parser for OverflowStrategy.
  */
object QueueOverflowStrategy {
  def apply(strategy: String): OverflowStrategy =
    strategy match {
      case "dropNew" ⇒ OverflowStrategy.dropNew
      case "dropHead" ⇒ OverflowStrategy.dropHead
      case "dropTail" ⇒ OverflowStrategy.dropTail
      case "dropBuffer" ⇒ OverflowStrategy.dropBuffer
      case "backpressure" ⇒ OverflowStrategy.backpressure
      case "fail" ⇒ OverflowStrategy.fail
      case _ ⇒ OverflowStrategy.dropNew
    }
}

/**
  * @param name                     The name for http flow
  * @param requestTimeout           Maximum duration before a request is considered timed out.
  * @param strictMaterializeTimeout Maximum duration for materialize the response entity when using strict method.
  * @param bufferSize               Maximum size for backpressure queue. If all connection ale in use, the request
  *                                 will wait there to be executed. The queue size should be bigger
  *                                 than akka.http.client.host-connection-pool.max-open-requests(default 32)
  * @param overflowStrategy         Queue backpressure strategy, What to do when the queue is full(default drop new request)
  */
case class BorisSettings(
    name: String,
    requestTimeout: FiniteDuration,
    strictMaterializeTimeout: FiniteDuration,
    bufferSize: Int,
    overflowStrategy: OverflowStrategy
) {

  require(requestTimeout > 0.seconds,
          "Request timeout must be larger than 0(zero)")
  require(strictMaterializeTimeout > 0.seconds,
          "Timeout for entity materialization must be larger than 0(zero)")
  require(bufferSize > 0, "Queue buffer size must be larger than 0(zero)")

  def withName(name: String): BorisSettings = this.copy(name = name)

  def withRequestTimeout(timeout: FiniteDuration): BorisSettings =
    this.copy(requestTimeout = timeout)

  def withStrictMaterializeTimeout(timeout: FiniteDuration): BorisSettings =
    this.copy(strictMaterializeTimeout = timeout)

  def withBufferSize(size: Int): BorisSettings = this.copy(bufferSize = size)

  def withOverflowStrategy(strategy: OverflowStrategy): BorisSettings =
    this.copy(overflowStrategy = strategy)
}

object BorisSettings {

  /**
    * Map settings from Hocon config to something Boris can eat
    *
    * @param config Typesafe Config object containing the boris settings
    * @return
    */
  def apply(config: Config): BorisSettings = {
    val name = config.getString("name")
    val requestTimeout =
      config.getDuration("request-timeout", TimeUnit.MILLISECONDS).millis
    val strictMaterializeTimeout =
      config.getDuration("materialize-timeout", TimeUnit.MILLISECONDS).millis
    val bufferSize = config.getInt("bufferSize")
    val overflowStrategy = QueueOverflowStrategy(
      config.getString("overflowStrategy"))

    BorisSettings(name,
                  requestTimeout,
                  strictMaterializeTimeout,
                  bufferSize,
                  overflowStrategy)
  }

  def apply(configOverrides: String): BorisSettings =
    apply(ConfigFactory.parseString(configOverrides))

  def apply(system: ActorSystem): BorisSettings =
    apply(system.settings.config.getConfig("horn.boris"))
}
