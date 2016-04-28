package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ Uri, HttpRequest, HttpResponse }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ ActorAttributes, Supervision, ActorMaterializer }
import akka.stream.scaladsl.{ Sink, Source }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Rest client dispatcher using an Akka http pooled connection to make the requests
 *
 * @param poolSettings Settings for this particular connection pool
 * @param system An actor system in which to execute the requests
 * @param materializer A flow materialiser
 */
case class RestClient(servers: Seq[Uri], poolSettings: ConnectionPoolSettings)(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {

  import system.dispatcher

  val decider: Supervision.Decider = {
    case _: ArithmeticException ⇒ Supervision.Resume
    case s ⇒
      println(s"${s.getMessage}")
      Supervision.Stop
  }
  private val pool = servers.map { u ⇒
    Http().cachedHostConnectionPool[Int](u.authority.host.address, u.authority.port, poolSettings)
  }.toVector

  var idx = 0

  def next = {
    val next = pool(idx)
    idx += 1
    if (idx == pool.length) idx = 0
    next
  }

  /**
   * Execute a single request using the connection pool.
   *
   * @param req An HttpRequest
   * @return The response
   */
  def exec(req: HttpRequest): Future[HttpResponse] = {
    Source.single(req → 1)
      .via(next)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .runWith(Sink.head).flatMap {
        case (Success(r: HttpResponse), _) ⇒ Future.successful(r)
        case (Failure(f), _) ⇒ Future.failed(f)
      }
  }

}
