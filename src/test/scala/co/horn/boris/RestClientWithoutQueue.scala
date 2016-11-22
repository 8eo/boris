package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class RestClientWithoutQueue(server: Uri,
                                  poolSettings: ConnectionPoolSettings,
                                  requestTimeout: FiniteDuration = 10 seconds)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer) {

  import system.dispatcher

  // A pool of Flows that will execute the HTTP requests via a connection pool
  private val pool = Flow[(HttpRequest, Int)]
    .via(Http().cachedHostConnectionPool[Int](server.authority.host.address, server.authority.port, poolSettings))

  /**
    * Execute a single request using the connection pool.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse] =
    Source.single(req → 1).via(pool).completionTimeout(requestTimeout).runWith(Sink.head).flatMap {
      case (Success(r: HttpResponse), _) ⇒ Future.successful(r)
      case (Failure(f), _) ⇒ Future.failed(f)
    }

}
