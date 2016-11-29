/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Interface into the ReST clients offered by Boris
  */
trait RestRequests {

  /**
    * Execute a single request using the connection pool.
    * You need to explicitly consume the entity (body) of the HttpResponse from the request.
    * Because the the entity of the response is actually a stream, it keeps the connection open
    * if you don't consume it. The Akka HTTP documentation details the request response cycle. Either
    * the server must send a `Connection: close` in the header or you must attach some
    * Sink (e.g. `Sink.ignore` or `Unmarshal(resp.entity).to[SomeClass]`) to consume the stream.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse]

  /**
    * Execute a single request using the connection pool but explicitly drop the response
    * entity.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execDrop(req: HttpRequest): Future[HttpResponse]

  /**
    * Execute a single request using the connection pool strictly consuming
    * the entity
    *
    * @param req An HttpRequest
    * @return The response
    */
  def execStrict(req: HttpRequest, timeout: Option[FiniteDuration] = None): Future[HttpResponse]
}

/**
  * Use batched requests to the selected client
  */
trait BatchRequests {

  /**
    * Take some sequence of requests and pipeline them through the connection pool.
    * Return whatever responses we get as a flattened sequence with the answers in the same
    * order as the original sequence. Zipping the request and response lists will result
    * in tuples of corresponding requests and responses. You must consume the responses.
    *
    * @param requests A list of requests that should be simultaneously issued to the pool
    * @return The responses in the same order as they were submitted
    */
  def execFlatten(requests: Iterable[HttpRequest], queueTypes: QueueTypes.QueueType): Future[Iterable[HttpResponse]]

  /**
    * Take some sequence of requests and pipeline them through the connection pool.
    * Return whatever responses we get as a sequence of futures that will be ordered
    * in such a way that zipping the request and response lists will result
    * in tuples of corresponding requests and responses. You must consume the responses
    *
    * @param requests A list of requests that should be simultaneously issued to the pool
    * @return The Future responses in the same order as they were submitted
    */
  def exec(requests: Iterable[HttpRequest], queueTypes: QueueTypes.QueueType): Iterable[Future[HttpResponse]]
}
