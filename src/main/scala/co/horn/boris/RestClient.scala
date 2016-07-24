package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.{Failure, Success}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param servers A list of URIs pointing to a set Òof functionally identical servers
  * @param requestTimeout Maximum duration before a request is considered timed out.
  * @param poolSettings Settings for this particular connection pool
  * @param system       An actor system in which to execute the requests
  * @param materializer A flow materialiser
  */
case class RestClient(servers: Seq[Uri], poolSettings: ConnectionPoolSettings, requestTimeout: Duration = 10 seconds)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer) {

  import java.util.concurrent.atomic.AtomicInteger

  import system.dispatcher

  // The flow needed to
  private val pool = servers.map { u ⇒
    Flow[(HttpRequest, Int)]
      .via(Http().cachedHostConnectionPool[Int](u.authority.host.address, u.authority.port, poolSettings))
  }

  val idx: AtomicInteger = new AtomicInteger()

  val maxTries = servers.size // Run once through the server pool before giving up

  def getIdx = idx.get() % pool.size

  def next = {
    val n = pool(idx.getAndIncrement() % pool.size)
    if (idx.get() == pool.length) idx.set(0)
    n
  }

  /**
    * Execute a single request using the connection pool. Note that there is no guarantee that the
    * servers will be called in exact sequence or that the error message will refer to the
    * actual server that failed as the {{ next }} function is not thread safe.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse] = {

    //
    def execHelper(request: HttpRequest, tries: Int): Future[HttpResponse] = {
      Source
        .single(request → getIdx) // Materialise this in a val so we don't have to create it each time
        .via(next)
        .completionTimeout(0.5 seconds) /// Make this configurable
        .runWith(Sink.head)
        .flatMap {
          case (Success(r: HttpResponse), _) ⇒
            system.log.info(s"Request to ${req.uri} succeeded on  ${servers(getIdx)}")
            Future.successful(r)
          case (Failure(f), p) if tries < maxTries ⇒
            system.log.warning(s"Request to ${request.uri} failed on ${servers(p)}: Failure was ${f.getMessage}")
            execHelper(req, tries + 1)
          case (Failure(f), _) if tries == maxTries ⇒
            system.log.error(s"Request to ${request.uri} failed (no servers available)")
            Future.failed(NoServersResponded)
          case (Failure(f), p) ⇒
            system.log.warning(s"Request to ${request.uri} failed on ${servers(p)}: Failure was ${f.getMessage}")
            Future.failed(f)
        } recoverWith {
        case t: TimeoutException ⇒
          if (tries < maxTries) {
            system.log.warning(s"Request to ${request.uri} timed out")
            execHelper(request, tries + 1)
          } else {
            system.log.error(s"Request failed after trying all servers")
            Future.failed(NoServersResponded)
          }
        case e ⇒
          system.log.error(s"Request failed due to unknown exception: $e")
          Future.failed(e)
      }
    }

    execHelper(req, 0)
  }
}
