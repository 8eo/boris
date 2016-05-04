package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Rest client dispatcher using an Akka http pooled connection to make the requests
  *
  * @param poolSettings Settings for this particular connection pool
  * @param system       An actor system in which to execute the requests
  * @param materializer A flow materialiser
  */
case class RestClient(servers: Seq[Uri], poolSettings: ConnectionPoolSettings)(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {

  import system.dispatcher
  import java.util.concurrent.atomic.AtomicInteger

  val decider: Supervision.Decider = {
    case _: ArithmeticException ⇒ Supervision.Resume // Arb error.... We need to check for other errors
    case t: TimeoutException ⇒
      println(s"********** Timeout!!! ${t.getMessage}")
      Supervision.Stop
    case s ⇒
      println(s"************************************** ${s.getMessage}")
      Supervision.Stop
  }

  private val pool = servers.map { u ⇒
    Http().cachedHostConnectionPool[Int](u.authority.host.address, u.authority.port, poolSettings)
  }.toVector

  val idx: AtomicInteger = new AtomicInteger()

  val maxTries = servers.size // Run once through the server pool before giving up

  def getIdx = idx.get() % pool.size

  def next = {
    val next = pool(idx.getAndIncrement() % pool.size)
    if (idx.get() == pool.length) idx.set(0)
    next
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

    def execHelper(request: HttpRequest, tries: Int): Future[HttpResponse] = {
      Source.single(request → getIdx) // Materialise this in a val so we don't have to create it each time
        .via(next)
        .completionTimeout(1 seconds) // TimeoutExceptions are not caught properly. Need to catch this
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
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
        }
    }

    execHelper(req, 0)
  }
}
