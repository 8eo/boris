package co.horn

import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}

package object boris {

  object QueueTypes extends Enumeration {
    type QueueType = Value
    val drop, strict, notConsumed = Value
  }

  /**
    * Get address string from Uri
    *
    * @param u The server Uri
    * @return Server address
    */
  def host(u: Uri): String = u.authority.host.address

  /**
    * Get port string from Uri
    *
    * @param u The server Uri
    * @return Server port
    */
  def port(u: Uri): Int = {
    val port = u.authority.port
    if (port == 0) {
      if (u.scheme == "https") 443 else 80
    } else port
  }

  /**
    * Pool connection queue, that's the way of handling back pressure in pools
    *
    * @param f Function that describes how to handle the response entity
    */
  def queue(
      pool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), HostConnectionPool],
      f: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit],
      bufferSize: Int,
      overflowStrategy: OverflowStrategy,
      name: String)(
      implicit materializer: ActorMaterializer): SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize, overflowStrategy)
      .named(name)
      .via(pool)
      .toMat(Sink.foreach(f))(Keep.left)
      .run

  /**
    * Drop entity
    */
  def drop(
      implicit materializer: ActorMaterializer): PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) =>
      resp.discardEntityBytes()
      p.success(resp)
    case ((Failure(e), p)) => p.failure(e)
  }

  /**
    * Convert entity to strict
    */
  def strict(toStrictTimeout: FiniteDuration)(
      implicit materializer: ActorMaterializer,
      ec: ExecutionContext): PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) =>
      p.completeWith(resp.toStrict(toStrictTimeout))
    case ((Failure(e), p)) => p.failure(e)
  }

  /**
    * The entity is not consumed, need to be handled manually
    */
  def notConsumed(
      implicit materializer: ActorMaterializer): PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit] = {
    case ((Success(resp), p)) => p.success(resp)
    case ((Failure(e), p)) => p.failure(e)
  }
}
