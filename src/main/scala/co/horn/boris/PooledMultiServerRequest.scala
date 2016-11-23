package co.horn.boris

import java.util.concurrent.TimeUnit
import java.util.function.IntUnaryOperator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.{ActorMaterializer, OverflowStrategy}
import co.horn.boris.QueueTypes.QueueType
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param servers                  A list of URIs pointing to a set of functionally identical servers
  * @param poolSettings             Settings for this particular connection pool
  * @param requestTimeout           Maximum duration before a request is considered timed out.
  * @param strictMaterializeTimeout Maximum duration for materialize the response entity when using strict method.
  * @param bufferSize               Maximum size for backpressure queue. If all connection ale in use, the request will wait there to be executed.
  *                                 should be bigger than akka.http.client.host-connection-pool.max-open-requests(default 32)
  * @param overflowStrategy         Queue backpressure strategy, What to do when the queue is full(default drop new request)
  * @param system                   An actor system in which to execute the requests
  * @param materializer             A flow materializer
  */

// TODO: Support https connections
@throws(classOf[IllegalArgumentException])
class PooledMultiServerRequest(servers: Seq[Uri],
                               poolSettings: ConnectionPoolSettings,
                               requestTimeout: FiniteDuration,
                               strictMaterializeTimeout: FiniteDuration,
                               bufferSize: Int,
                               overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends RestRequests {

  require(servers.nonEmpty, "Servers list cannot be empty.")
  require(requestTimeout > 0.seconds, "Request timeout must be larger than 0(zero)")
  require(strictMaterializeTimeout > 0.seconds, "Timeout for entity materialization must be larger than 0(zero)")
  require(requestTimeout > strictMaterializeTimeout,
          "Request timeout must be larger than timeout for entity materialization")
  require(bufferSize > 0, "Queue buffer size must be larger than 0(zero)")

  import java.util.concurrent.atomic.AtomicInteger

  import system.dispatcher

  // A pool of Flows that will execute the HTTP requests via a connection pool
  private val queues = servers.map {
    case u if u.scheme == "https" ⇒
      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](u.authority.host.address,
                                                                  u.authority.port,
                                                                  settings = poolSettings)
    case u ⇒
      Http().cachedHostConnectionPool[Promise[HttpResponse]](u.authority.host.address, u.authority.port, poolSettings)
  }.map { pool ⇒
    Map(QueueTypes.drop → queue(pool, drop, bufferSize, overflowStrategy, ""),
        QueueTypes.strict → queue(pool, strict(strictMaterializeTimeout), bufferSize, overflowStrategy, ""),
        QueueTypes.notConsumed → queue(pool, notConsumed, bufferSize, overflowStrategy, ""))
  }

  private val poolIndex: AtomicInteger = new AtomicInteger()

  private val poolSize = queues.size // Run once through the server pool before giving up

  /**
    * Determines next connection pool to use, returning both the pool and
    * the index number of the pool in a thread safe way
    *
    * @return A tuple (pool index, pool)
    */
  private def nextFlow(queue: QueueType) = {
    val n = poolIndex.getAndUpdate(new IntUnaryOperator {
      override def applyAsInt(operand: Int): Int = (operand + 1) % poolSize
    })
    n → queues(n)(queue)
  }

  /**
    * @inheritdoc
    */
  override def exec(req: HttpRequest): Future[HttpResponse] = execHelper(req, QueueTypes.notConsumed)

  /**
    * @inheritdoc
    */
  override def execDrop(req: HttpRequest): Future[HttpResponse] = execHelper(req, QueueTypes.drop)

  /**
    * @inheritdoc
    */
  override def execStrict(req: HttpRequest): Future[HttpResponse] = execHelper(req, QueueTypes.strict)

  private def execHelper(request: HttpRequest, queueType: QueueType, tries: Int = 0): Future[HttpResponse] = {
    import co.horn.boris.utils.FutureUtils.FutureWithTimeout
    val (idx, flow) = nextFlow(queueType)
    val promise = Promise[HttpResponse]
    flow
      .offer(request -> promise)
      .flatMap {
        case Enqueued ⇒ promise.future
        case other ⇒ Future.failed(EnqueueRequestFails(other))
      }
      .withTimeout(requestTimeout)
      .recoverWith {
        case t: Throwable if tries < poolSize ⇒
          system.log.warning(s"Request to ${request.uri} failed on ${servers(idx)}: Failure was ${t.getMessage}")
          execHelper(request, queueType, tries + 1)
        case t: Throwable if tries >= poolSize ⇒
          system.log.error(s"Request to ${request.uri} failed (no servers available), : Failure was ${t.getMessage}")
          Future.failed(NoServersResponded(t))
        case e ⇒
          system.log.error(s"Request failed due to unknown exception: $e")
          Future.failed(e)
      }
  }

}

object PooledMultiServerRequest {

  def apply(uri: Uri, poolSettings: ConnectionPoolSettings, config: Config)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledMultiServerRequest = {
    apply(Seq(uri), poolSettings, config)(system, materializer)
  }

  def apply(uri: Uri, poolSettings: ConnectionPoolSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledMultiServerRequest = {
    apply(Seq(uri), poolSettings, system.settings.config)(system, materializer)
  }

  def apply(servers: Seq[Uri], poolSettings: ConnectionPoolSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledMultiServerRequest = {
    apply(servers, poolSettings, system.settings.config)(system, materializer)
  }

  def apply(servers: Seq[Uri], poolSettings: ConnectionPoolSettings, config: Config)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledMultiServerRequest = {
    val conf = config.getConfig("horn.boris")
    val requestTimeout = conf.getDuration("request-timeout", TimeUnit.MILLISECONDS).millis
    val strictMaterializeTimeout = conf.getDuration("materialize-timeout", TimeUnit.MILLISECONDS).millis
    val bufferSize = conf.getInt("bufferSize")
    new PooledMultiServerRequest(servers, poolSettings, requestTimeout, strictMaterializeTimeout, bufferSize)(
      system,
      materializer)
  }
}
