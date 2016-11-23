/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class SingleServerRequest() extends RestRequests {

  /**
    * @inheritdoc
    */
  override def exec(req: HttpRequest): Future[HttpResponse] = ???

  /**
    * @inheritdoc
    */
  override def execDrop(req: HttpRequest): Future[HttpResponse] = ???

  /**
    * @inheritdoc
    */
  override def execStrict(req: HttpRequest): Future[HttpResponse] = ???

}
