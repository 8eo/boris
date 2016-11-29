package co.horn.boris

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer

object RestClientFactory {

  /**
    * Construct a pooled connection to a single server using custom configuration settings.
    *
    * @param server                 The server uri
    * @param connectionPoolSettings The pool connection settings
    * @param settings               Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return                       A client against which requests can be made
    */
  def poolClient(server: Uri, connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests =
    PooledSingleServerRequest(server, connectionPoolSettings, settings)

  /**
    * Construct a pooled connection to a single server using the default configuration settings in `horn.boris`.
    *
    * @param server The server uri
    * @return                       A client against which requests can be made
    */
  def poolClient(server: Uri)(implicit system: ActorSystem, materializer: ActorMaterializer): RestRequests =
    poolClient(server, ConnectionPoolSettings(system), BorisSettings(system))

  /**
    * Construct a pooled connection to multiple servers using custom configurations. The servers will receive
    * requests in a Round Robin schedule. If a server or a request fails, the request is sent to the next server
    * in the list.
    *
    * @param servers                The list of server URIs
    * @param connectionPoolSettings The pool connection settings
    * @param settings               Boris rest client settings [[BorisSettings]], check `horn.boris` configuration
    * @return                       A client against which requests can be made
    */
  def multiPoolClient(servers: Seq[Uri], connectionPoolSettings: ConnectionPoolSettings, settings: BorisSettings)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer): RestRequests =
    PooledMultiServerRequest(servers, connectionPoolSettings, settings)

  /**
    * Construct a pooled connection to multiple servers using the default configurations. The servers will receive
    * requests in a Round Robin schedule. If a server or a request fails, the request is sent to the next server
    * in the list.
    *
    * @param servers   The list of servers URI
    * @return          A client against which requests can be made
    */
  def multiPoolClient(servers: Seq[Uri])(implicit system: ActorSystem, materializer: ActorMaterializer): RestRequests =
    multiPoolClient(servers, ConnectionPoolSettings(system), BorisSettings(system))

  /**
    * Construct a simple connection to a single client using a separate connection for each request.
    *
    * @param server   The server uri
    * @param settings Boris rest client settings [[BorisSettings]], check `horn.boris` configuration.
    * @return         A client against which requests can be made.
    */
  def singleClient(server: Uri, settings: BorisSettings)(implicit system: ActorSystem,
                                                         materializer: ActorMaterializer): RestRequests =
    SingleServerRequest(server, settings)

  /**
    *  Construct a simple connection to a single client using a separate connection for each request.
    * It will use default BorisSettings (from system config)
    *
    * @param server   The server uri
    * @return         A client against which requests can be made
    */
  def singleClient(server: Uri)(implicit system: ActorSystem, materializer: ActorMaterializer): RestRequests =
    SingleServerRequest(server, BorisSettings(system))

}
