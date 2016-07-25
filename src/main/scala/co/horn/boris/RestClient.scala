package co.horn.boris

import java.util.function.IntUnaryOperator

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
case class RestClient(servers: Seq[Uri], poolSettings: ConnectionPoolSettings, requestTimeout: FiniteDuration = 10 seconds)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer) {

  import java.util.concurrent.atomic.AtomicInteger

  import system.dispatcher

  // A pool of Flows that will execute the HTTP requests via a connection pool
  private val pool = servers.map { u ⇒
    Flow[(HttpRequest, Int)]
      .via(Http().cachedHostConnectionPool[Int](u.authority.host.address, u.authority.port, poolSettings))
  }

  val poolIndex: AtomicInteger = new AtomicInteger()

  val poolSize = pool.size // Run once through the server pool before giving up

  /**
    * Determines next connection pool to use, returning both the pool and
    * the index number of the pool in a thread safe way
    * @return A tuple (pool index, pool)
    */
  def nextFlow = {
    val n = poolIndex.getAndUpdate(new IntUnaryOperator{
      override def applyAsInt(operand: Int): Int = (operand + 1) % poolSize
    })
    n → pool(n)
  }

  /**
    * Execute a single request using the connection pool.
    *
    * @param req An HttpRequest
    * @return The response
    */
  def exec(req: HttpRequest): Future[HttpResponse] = {

    def execHelper(request: HttpRequest, tries: Int): Future[HttpResponse] = {
      val (idx, flow) = nextFlow
      Source
        .single(request → idx)
        .via(flow)
        .completionTimeout(requestTimeout)
        .runWith(Sink.head)
        .flatMap {
          case (Success(r: HttpResponse), _) ⇒
            system.log.info(s"Request to ${req.uri} succeeded on  ${servers(idx)}")
            Future.successful(r)
          case (Failure(f), p) if tries < poolSize ⇒
            system.log.warning(s"Request to ${request.uri} failed on ${servers(p)}: Failure was ${f.getMessage}")
            execHelper(req, tries + 1)
          case (Failure(f), _) if tries == poolSize ⇒
            system.log.error(s"Request to ${request.uri} failed (no servers available)")
            Future.failed(NoServersResponded)
        } recoverWith {
        case t: TimeoutException ⇒
          if (tries < poolSize) {
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
