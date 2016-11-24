/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

import akka.stream.QueueOfferResult

trait BorisError extends Throwable

/**
  * All servers failed and are marked as dead now.
  */
case object AllServerAreMarkedAsDead extends BorisError
/**
  * None of the available servers was able to return proper response.
  * @param cause What caused the the failure.
  */
case class NoServersResponded(cause: Throwable) extends BorisError

/**
  * Adding request to pool connection queue fails
  */
case class EnqueueRequestFails(reason: QueueOfferResult) extends BorisError
