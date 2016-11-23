/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Future
import co.horn.boris.utils.FutureUtils.FutureWithTimeout

/**
  * Just sends a simple HTTP request that creates a new connection for each request. Note that you configure
  * it with a server and a port specified in the URI (e.g. http://google.com:2020). The URI specified in the
  * actual calls are just used to specify the path. This is done for compatibility with the pooled connection
  * services where there is already a connection to the target server(s).
  *
  * @param server A URI pointing to the server
  */
private[boris] class SingleServerRequest(server: Uri,
                                         requestTimeout: FiniteDuration,
                                         strictMaterializeTimeout: FiniteDuration)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends RestRequests {

  import system.dispatcher

  private def setReq(req: HttpRequest): HttpRequest = req.withUri(server.withPath(req.uri.path))

  /**
    * @inheritdoc
    */
  override def exec(req: HttpRequest): Future[HttpResponse] =
    Http().singleRequest(setReq(req)).withTimeout(requestTimeout)

  /**
    * @inheritdoc
    */
  override def execDrop(req: HttpRequest): Future[HttpResponse] =
    Http()
      .singleRequest(setReq(req))
      .map { resp ⇒
        resp.discardEntityBytes()
        resp
      }
      .withTimeout(requestTimeout)

  /**
    * @inheritdoc
    */
  override def execStrict(req: HttpRequest): Future[HttpResponse] =
    Http().singleRequest(setReq(req)).flatMap(_.toStrict(strictMaterializeTimeout)).withTimeout(requestTimeout)

}

object SingleServerRequest {
  def apply(uri: Uri, settings: BorisSettings)(implicit system: ActorSystem,
                                               materializer: ActorMaterializer): SingleServerRequest = {
    new SingleServerRequest(uri, settings.requestTimeout, settings.strictMaterializeTimeout)
  }
}
