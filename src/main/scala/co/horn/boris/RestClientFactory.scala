package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer

object RestClientFactory {

  def poolClient(server: Uri, connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests with BatchRequests =
    PooledSingleServerRequest(server, connectionPoolSettings, settings)

  def multiPoolClient(servers: Seq[Uri], connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests =
    PooledMultiServerRequest(servers, connectionPoolSettings, settings)

  def singleClient(server: Uri, settings: BorisSettings)(implicit system: ActorSystem,
                                                         materializer: ActorMaterializer): RestRequests = ???
}
