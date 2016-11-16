/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.stream.QueueOfferResult

trait BorisError extends Throwable

case object NoServersResponded extends BorisError

/**
  * Adding request to pool connection queue fails
  */
case class EnqueueRequestFails(reason: QueueOfferResult) extends BorisError
