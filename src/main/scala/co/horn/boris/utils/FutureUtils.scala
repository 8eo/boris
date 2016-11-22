package co.horn.boris.utils

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object FutureUtils {

  implicit class FutureWithTimeout[T](future: Future[T]) {

    import akka.pattern.after

    def withTimeout(timeout: FiniteDuration)(implicit system: ActorSystem, ec: ExecutionContext): Future[T] = {
      Future.firstCompletedOf(future :: after(timeout, system.scheduler)(Future.failed(new TimeoutException)) :: Nil)
    }
  }

}
