package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import co.horn.boris.utils.StreamUtils

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param servers                  A list of URIs pointing to a set of functionally identical servers
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
private[boris] class PooledMultiServerRequest(
    servers: Seq[Uri],
    poolSettings: ConnectionPoolSettings,
    name: String,
    requestTimeout: FiniteDuration,
    strictMaterializeTimeout: FiniteDuration,
    bufferSize: Int,
    overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew
)(implicit val system: ActorSystem, implicit val materializer: Materializer)
    extends RestRequests {

  require(servers.nonEmpty, "Servers list cannot be empty.")

  import system.dispatcher

  // A pool of Flows that will execute the HTTP requests via a connection pool
  private val pools = servers.map {
    case u if u.scheme == "https" ⇒
      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](host(u), port(u), settings = poolSettings)
    case u ⇒
      Http().cachedHostConnectionPool[Promise[HttpResponse]](host(u), port(u), poolSettings)
  }

  private val queue = Source
    .queue[(HttpRequest, Promise[HttpResponse])](bufferSize, overflowStrategy)
    .named(name)
    .via(StreamUtils.balancer(pools))
    .toMat(Sink.foreach {
      case ((Success(resp), p)) => p.success(resp)
      case ((Failure(e), p))    => p.failure(e)
    })(Keep.left)
    .run

  /**
    * @inheritdoc
    */
  override def exec(req: HttpRequest): Future[HttpResponse] = execHelper(req)

  /**
    * @inheritdoc
    */
  override def execDrop(req: HttpRequest): Future[HttpResponse] =
    execHelper(req).map { resp ⇒
      resp.discardEntityBytes()
      resp
    }

  /**
    * @inheritdoc
    */
  override def execStrict(req: HttpRequest, timeout: Option[FiniteDuration] = None): Future[HttpResponse] =
    execHelper(req).flatMap(_.toStrict(timeout.getOrElse(strictMaterializeTimeout)))

  private def execHelper(request: HttpRequest, tries: Int = 0): Future[HttpResponse] = {
    import co.horn.boris.utils.FutureUtils.FutureWithTimeout
    val promise = Promise[HttpResponse]
    queue
      .offer(request -> promise)
      .flatMap {
        case Enqueued ⇒ promise.future
        case other    ⇒ Future.failed(EnqueueRequestFails(other))
      }
      .withTimeout(requestTimeout)
      .recoverWith {
        case t: Throwable if tries < pools.size ⇒
          system.log.warning(
            s"Request to ${request.uri} failed: Failure was ${t.toString} with message ${t.getMessage}"
          )
          execHelper(request, tries + 1)
        case t: Throwable ⇒
          system.log.error(
            s"Request to ${request.uri} failed (no servers available), : Failure was ${t.toString} with message ${t.getMessage}"
          )
          Future.failed(NoServersResponded(t))
      }
  }
}

object PooledMultiServerRequest {

  /**
    * Constructor of multiple servers pool client.
    *
    * @param servers      The list of servers URI
    * @param poolSettings The pool connection settings
    * @param settings     Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return PooledMultiServerRequest rest client
    */
  def apply(servers: Seq[Uri], poolSettings: ConnectionPoolSettings, settings: BorisSettings)(implicit
      system: ActorSystem,
      materializer: Materializer
  ): PooledMultiServerRequest = {
    new PooledMultiServerRequest(
      servers,
      poolSettings,
      settings.name,
      settings.requestTimeout,
      settings.strictMaterializeTimeout,
      settings.bufferSize,
      settings.overflowStrategy
    )
  }
}
