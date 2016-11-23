/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param server                   The target server's uri
  * @param poolSettings             Settings for this particular connection pool
  * @param name                     The name for http flow
  * @param requestTimeout           Maximum duration before a request is considered timed out.
  * @param strictMaterializeTimeout Maximum duration for materialize the response entity when using strict method.
  * @param bufferSize               Maximum size for backpressure queue. If all connection ale in use, the request will wait there to be executed.
  *                                 should be bigger than akka.http.client.host-connection-pool.max-open-requests(default 32)
  * @param overflowStrategy         Queue backpressure strategy, What to do when the queue is full(default drop new request)
  * @param system                   An actor system in which to execute the requests
  * @param materializer             A flow materializer
  */
private[boris] class PooledSingleServerRequest(server: Uri,
                                               poolSettings: ConnectionPoolSettings,
                                               name: String,
                                               requestTimeout: FiniteDuration,
                                               strictMaterializeTimeout: FiniteDuration,
                                               bufferSize: Int,
                                               overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends RestRequests
    with BatchRequests {

  import system.dispatcher

  private val pool =
    if (server.scheme == "https") {
      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](server.authority.host.address,
                                                                  server.authority.port,
                                                                  settings = poolSettings)
    } else {
      Http().cachedHostConnectionPool[Promise[HttpResponse]](server.authority.host.address,
                                                             server.authority.port,
                                                             poolSettings)
    }

  private val dropQueue = queue(pool, drop, bufferSize, overflowStrategy, name)

  private val strictQueue = queue(pool, strict(strictMaterializeTimeout), bufferSize, overflowStrategy, name)

  private val notConsumedQueue = queue(pool, notConsumed, bufferSize, overflowStrategy, name)

  /**
    * Execute a single request using the connection pool. Callers ABSOLUTELY HAVE TO
    * CONSUME THE RESPONSE ENTITY
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse] = execHelper(req, notConsumedQueue)

  /**
    * Execute a single request using the connection pool but explicitly drop the response
    * entity.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execDrop(req: HttpRequest): Future[HttpResponse] = execHelper(req, strictQueue)

  /**
    * Execute a single request using the connection pool strictly consuming
    * the entity
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execStrict(req: HttpRequest): Future[HttpResponse] = execHelper(req, strictQueue)

  private def execHelper(
      request: HttpRequest,
      queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])]): Future[HttpResponse] = {
    import co.horn.boris.utils.FutureUtils.FutureWithTimeout
    val promise = Promise[HttpResponse]
    queue
      .offer(request -> promise)
      .flatMap {
        case Enqueued ⇒ promise.future
        case other ⇒ Future.failed(EnqueueRequestFails(other))
      }
      .withTimeout(requestTimeout)
  }

  /**
    * Take some sequence of requests and pipeline them through the connection pool.
    * Return whatever responses we get as a flattened sequence with the answers in the same
    * order as the original sequence. Zipping the request and response lists will result
    * in tuples of corresponding requests and responses
    *
    * @param requests A list of requests that should be simultaneously issued to the pool
    * @return The responses in the same order as they were submitted
    */
  def execFlatten(requests: Iterable[HttpRequest], queueTypes: QueueTypes.QueueType): Future[Iterable[HttpResponse]] = {
    Future.sequence(exec(requests, queueTypes))
  }

  /**
    * Take some sequence of requests and pipeline them through the connection pool.
    * Return whatever responses we get as a sequence of futures that will be ordered
    * in such a way that zipping the request and response lists will result
    * in tuples of corresponding requests and responses.
    *
    * @param requests A list of requests that should be simultaneously issued to the pool
    * @return The Future responses in the same order as they were submitted
    */
  def exec(requests: Iterable[HttpRequest], queueTypes: QueueTypes.QueueType): Iterable[Future[HttpResponse]] = {
    val f = queueTypes match {
      case QueueTypes.drop ⇒ dropQueue
      case QueueTypes.strict ⇒ strictQueue
      case QueueTypes.notConsumed ⇒ notConsumedQueue
    }
    requests.map(r ⇒ execHelper(r, f))
  }
}

object PooledSingleServerRequest {
  def apply(uri: Uri, poolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledSingleServerRequest = {
    new PooledSingleServerRequest(uri,
                                  poolSettings,
                                  settings.name,
                                  settings.requestTimeout,
                                  settings.strictMaterializeTimeout,
                                  settings.bufferSize,
                                  settings.overflowStrategy)
  }
}
