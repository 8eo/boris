package co.horn

import akka.http.scaladsl.model.Uri

package object boris {

  /**
    * Get address string from Uri
    *
    * @param u The server Uri
    * @return Server address
    */
  def host(u: Uri): String = u.authority.host.address

  /**
    * Get port string from Uri
    *
    * @param u The server Uri
    * @return Server port
    */
  def port(u: Uri): Int = {
    val port = u.authority.port
    if (port == 0) {
      if (u.scheme == "https") 443 else 80
    } else port
  }
}
