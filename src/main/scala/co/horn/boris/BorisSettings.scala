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

/**
  * @param failureThreshold When servers reach that number of failures it will be considered as dead.
  * @param suspendTime Amount of time server will be suspended when it will be considered as dead.
  * @param availableServersMinimum Minimum count of available servers
  */
case class DeadServerStrategy(failureThreshold: Int, suspendTime: FiniteDuration, availableServersMinimum: Int)

/**
  * @param name                     The name for http flow
  * @param requestTimeout           Maximum duration before a request is considered timed out.
  * @param strictMaterializeTimeout Maximum duration for materialize the response entity when using strict method.
  * @param bufferSize               Maximum size for backpressure queue. If all connection ale in use, the request will wait there to be executed.
  *                                 should be bigger than akka.http.client.host-connection-pool.max-open-requests(default 32)
  * @param overflowStrategy         Queue backpressure strategy, What to do when the queue is full(default drop new request)
  * @param deadServerStrategy       Describes if and when the server is Considered as dead and for how long it will be suspended.
  */
@throws(classOf[IllegalArgumentException])
case class BorisSettings(name: String,
                         requestTimeout: FiniteDuration,
                         strictMaterializeTimeout: FiniteDuration,
                         bufferSize: Int,
                         overflowStrategy: OverflowStrategy,
                         deadServerStrategy: Option[DeadServerStrategy]) {

  require(requestTimeout > 0.seconds, "Request timeout must be larger than 0(zero)")
  require(strictMaterializeTimeout > 0.seconds, "Timeout for entity materialization must be larger than 0(zero)")
  require(bufferSize > 0, "Queue buffer size must be larger than 0(zero)")
  require(deadServerStrategy.forall(_.failureThreshold > 0), "Failure threshold must be larger than 0(zero)")
  require(deadServerStrategy.forall(_.availableServersMinimum >= 0),
          "Minimum count of available servers cannot be lower than 0(zero)")

  def withName(name: String): BorisSettings = this.copy(name = name)

  def withRequestTimeout(timeout: FiniteDuration): BorisSettings = this.copy(requestTimeout = timeout)

  def withStrictMaterializeTimeout(timeout: FiniteDuration): BorisSettings =
    this.copy(strictMaterializeTimeout = timeout)

  def withBufferSize(size: Int): BorisSettings = this.copy(bufferSize = size)

  def withOverflowStrategy(strategy: OverflowStrategy): BorisSettings = this.copy(overflowStrategy = strategy)

  def withDeadServerStrategy(strategy: DeadServerStrategy): BorisSettings =
    this.copy(deadServerStrategy = Some(strategy))

  def withOutDeadServerStrategy(): BorisSettings = this.copy(deadServerStrategy = None)
}

object BorisSettings {
  def apply(config: Config): BorisSettings = {
    val name = config.getString("name")
    val requestTimeout = config.getDuration("request-timeout", TimeUnit.MILLISECONDS).millis
    val strictMaterializeTimeout = config.getDuration("materialize-timeout", TimeUnit.MILLISECONDS).millis
    val bufferSize = config.getInt("bufferSize")
    val overflowStrategy = QueueOverflowStrategy(config.getString("overflowStrategy"))
    val deadServerStrategy = if (config.getBoolean("dead-server.enabled")) {
      Some(
        DeadServerStrategy(config.getInt("dead-server.failure-threshold"),
                           config.getDuration("dead-server.suspend-time", TimeUnit.MILLISECONDS).millis,
                           config.getInt("dead-server.min-available-servers")))
    } else None
    BorisSettings(name, requestTimeout, strictMaterializeTimeout, bufferSize, overflowStrategy, deadServerStrategy)
  }

  def apply(configOverrides: String): BorisSettings = apply(ConfigFactory.parseString(configOverrides))

  def apply(system: ActorSystem): BorisSettings = apply(system.settings.config.getConfig("horn.boris"))
}
