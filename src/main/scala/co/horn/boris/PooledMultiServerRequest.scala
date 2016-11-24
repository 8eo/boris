package co.horn.boris

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.{ActorMaterializer, OverflowStrategy}
import co.horn.boris.QueueTypes.QueueType
import co.horn.boris.utils.Ref

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/**
  * Object that store information about server failures.
  *
  * @param id            Id of server
  * @param failureCount  Server failure counter.
  * @param suspendedTill Option that keep information if server has been considered as dead and for how long it will be out of use.
  */
private[boris] case class ServerFailure(id: Int, failureCount: Int, suspendedTill: Option[Long])

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
  * @param deadServerStrategy       Describes if and when the server is Considered as dead and for how long it will be suspended.
  * @param system                   An actor system in which to execute the requests
  * @param materializer             A flow materializer
  */
@throws(classOf[IllegalArgumentException])
private[boris] class PooledMultiServerRequest(servers: Seq[Uri],
                                              poolSettings: ConnectionPoolSettings,
                                              name: String,
                                              requestTimeout: FiniteDuration,
                                              strictMaterializeTimeout: FiniteDuration,
                                              bufferSize: Int,
                                              overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew,
                                              deadServerStrategy: Option[DeadServerStrategy])(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends RestRequests {

  require(servers.nonEmpty, "Servers list cannot be empty.")

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
    Map(QueueTypes.drop → queue(pool, drop, bufferSize, overflowStrategy, name),
        QueueTypes.strict → queue(pool, strict(strictMaterializeTimeout), bufferSize, overflowStrategy, name),
        QueueTypes.notConsumed → queue(pool, notConsumed, bufferSize, overflowStrategy, name))
  }

  private val poolIndex: AtomicInteger = new AtomicInteger()
  private val poolSize = queues.size
  private val serverFailures = Ref(servers.zipWithIndex.map(r ⇒ ServerFailure(r._2, 0, None)))

  /**
    * Determines next connection pool to use, returning both the pool and
    * the index number of the pool in a thread safe way
    *
    * @return A tuple (pool index, pool)
    */
  private def nextFlow(queue: QueueType) = {
    val n = poolIndex.getAndUpdate(new IntUnaryOperator {
      override def applyAsInt(operand: Int): Int = {
        //find all server that are not suspended
        def availableServers() =
          serverFailures.get.filter(_.suspendedTill.forall(time ⇒ time < System.currentTimeMillis)).map(_.id)
        @tailrec
        def _pickServer(id: Int, ids: Seq[Int]): Int = {
          if (ids.contains(id)) id
          else _pickServer((id + 1) % poolSize, availableServers())
        }
        _pickServer((operand + 1) % poolSize, availableServers())
      }
    })
    n → queues(n)(queue)
  }

  /**
    * Count server failures, and mark server as dead when failures count reaches the threshold.
    * That function is thread safe
    *
    * @param id Id of server that failed
    */
  private def serverFailure(id: Int) = deadServerStrategy.map { strategy ⇒
    serverFailures.transformAndGet { servers ⇒
      val failureCount = servers(id).failureCount + 1
      val deadServers = servers.count(_.suspendedTill.isDefined)
      // check if failure Count of given server reached threshold, than check if the number of available servers is enough
      val suspension =
        if (failureCount >= strategy.failureThreshold && deadServers <= strategy.availableServersMinimum) {
          Some(System.currentTimeMillis + strategy.suspendTime.toMillis)
        } else None
      servers.updated(id, ServerFailure(id, failureCount, suspension))
    }
  }

  /**
    * Reset the failures counter of server when it is reachable.
    *
    * @param id Id of server that execute request properly
    */
  private def serverSuccess(id: Int) = if (deadServerStrategy.isDefined) {
    serverFailures.transformAndGet { servers ⇒
      val count = Math.max(0, servers(id).failureCount - 1)
      servers.updated(id, ServerFailure(id, count, None))
    }
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
      .map { resp ⇒
        serverSuccess(idx)
        resp
      }
      .recoverWith {
        case t: Throwable if tries < poolSize ⇒
          serverFailure(idx)
          system.log.warning(s"Request to ${request.uri} failed on ${servers(idx)}: Failure was ${t.getMessage}")
          execHelper(request, queueType, tries + 1)
        case t: Throwable if tries >= poolSize ⇒
          serverFailure(idx)
          system.log.error(s"Request to ${request.uri} failed (no servers available), : Failure was ${t.getMessage}")
          Future.failed(NoServersResponded(t))
      }
  }
}

object PooledMultiServerRequest {

  /**
    * Constructor of multiple servers pool client.
    *
    * @param servers The list of servers URI
    * @param poolSettings The pool connection settings
    * @param settings Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return PooledMultiServerRequest rest client
    */
  def apply(servers: Seq[Uri], poolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): PooledMultiServerRequest = {
    new PooledMultiServerRequest(servers,
                                 poolSettings,
                                 settings.name,
                                 settings.requestTimeout,
                                 settings.strictMaterializeTimeout,
                                 settings.bufferSize,
                                 settings.overflowStrategy,
                                 settings.deadServerStrategy)
  }
}
