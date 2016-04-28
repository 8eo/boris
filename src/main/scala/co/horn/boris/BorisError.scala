/*
 * Copyright © ${year} 8eo Inc.
 */
package co.horn.boris

trait  BorisError extends Throwable

case object NoServersResponded extends BorisError
