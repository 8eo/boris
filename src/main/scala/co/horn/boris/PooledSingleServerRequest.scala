/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param address      The target server's address
  * @param port         The target server's address
  * @param secure       If the connection is using https
  * @param poolSettings Settings for this particular connection pool
  * @param buffer       The size of queue that will handle back pressure on pull connection ( 0 means infinite)
  * @param system       An actor system in which to execute the requests
  * @param materializer A flow materialiser
  */
case class PooledSingleServerRequest(
    address: String,
    port: Int,
    secure: Boolean,
    poolSettings: ConnectionPoolSettings,
    buffer: Int)(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer)
    extends RestRequests
    with BatchRequests {

  import system.dispatcher

  private val pool =
    if (secure)
      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](address, port, settings = poolSettings)
    else
      Http().cachedHostConnectionPool[Promise[HttpResponse]](address, port, poolSettings)

  /**
    * Pool connection queue, that's the way of handling back pressure in pools
    *
    * @param f Function that describes how to handle the response entity
    */
  private def _queue(f: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit]) =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](buffer, OverflowStrategy.dropNew)
      .named(s"Connection to: $address:$port")
      .via(pool)
      .toMat(Sink.foreach(f))(Keep.left)
      .run

  /**
    * Drop entity
    */
  private val _drop: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) =>
      resp.discardEntityBytes()
      p.success(resp)
    case ((Failure(e), p)) => p.failure(e)
  }

  private val dropQueue = _queue(_drop)

  /**
    * Convert entity to strict
    */
  private val _strict: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) =>
      resp.toStrict(10 seconds).map(p.success)
    case ((Failure(e), p)) => p.failure(e)
  }

  private val strictQueue = _queue(_strict)

  /**
    * The entity is not consumed, need to be handled manually
    */
  private val _notConsumed: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) => p.success(resp)
    case ((Failure(e), p)) => p.failure(e)
  }

  private val notConsumedQueue = _queue(_notConsumed)

  /**
    * Execute a single request using the connection pool. Callers ABSOLUTELY HAVE TO
    * CONSUME THE RESPONSE ENTITY
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]
    notConsumedQueue.offer(req -> promise).flatMap {
      case Enqueued ⇒ promise.future
      case other ⇒ Future.failed(EnqueueRequestFails(other))
    }
  }

  /**
    * Execute a single request using the connection pool but explicitly drop the response
    * entity.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execDrop(req: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]
    dropQueue.offer(req -> promise).flatMap {
      case Enqueued ⇒ promise.future
      case other ⇒ Future.failed(EnqueueRequestFails(other))
    }
  }

  /**
    * Execute a single request using the connection pool strictly consuming
    * the entity
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execStrict(req: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]
    strictQueue.offer(req -> promise).flatMap {
      case Enqueued ⇒ promise.future
      case other ⇒ Future.failed(EnqueueRequestFails(other))
    }
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
  def execFlatten(requests: Iterable[HttpRequest]): Future[Iterable[HttpResponse]] = {
    exec(requests).flatMap(Future.sequence(_))
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
  def exec(requests: Iterable[HttpRequest]): Future[Iterable[Future[HttpResponse]]] = {
    val _req = requests.map(r ⇒ r → Promise[HttpResponse]).toMap
    Source(_req).via(pool).runWith(Sink.foreach(_notConsumed)).map(_ ⇒ _req.values.map(_.future))
  }
}
