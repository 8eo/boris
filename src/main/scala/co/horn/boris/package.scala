package co.horn

import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
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
    * Pool connection queue, that's the way of handling back pressure in pools
    *
    * @param f Function that describes how to handle the response entity
    */
  def queue(
      pool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), HostConnectionPool],
      f: PartialFunction[(Try[HttpResponse], Promise[HttpResponse]), Unit],
      bufferSize: Int,
      overflowStrategy: OverflowStrategy,
      name: String)(implicit materializer: ActorMaterializer) =
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
