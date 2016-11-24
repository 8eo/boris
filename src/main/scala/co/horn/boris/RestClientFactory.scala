package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer

object RestClientFactory {

  /**
    * Constructor of pool connection rest client.
    *
    * @param server The server uri
    * @param connectionPoolSettings The pool connection settings
    * @param settings Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return PooledMultiServerRequest rest client
    */
  def poolClient(server: Uri, connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests with BatchRequests =
    PooledSingleServerRequest(server, connectionPoolSettings, settings)

  /**
    * Constructor of multiple servers pool client.
    *
    * @param servers The list of servers URI
    * @param connectionPoolSettings The pool connection settings
    * @param settings Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return PooledMultiServerRequest rest client
    */
  def multiPoolClient(servers: Seq[Uri], connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests =
    PooledMultiServerRequest(servers, connectionPoolSettings, settings)

  /**
    * Constructor of single connection rest client.
    *
    * @param server The server uri
    * @param settings Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return PooledMultiServerRequest rest client
    */
  def singleClient(server: Uri, settings: BorisSettings)(implicit system: ActorSystem,
                                                         materializer: ActorMaterializer): RestRequests =
    SingleServerRequest(server, settings)

}
