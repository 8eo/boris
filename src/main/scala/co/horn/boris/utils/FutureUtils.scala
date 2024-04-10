package co.horn.boris.utils

import akka.actor.ActorSystem

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object FutureUtils {

  implicit class FutureWithTimeout[T](future: Future[T]) {

    import akka.pattern.after

    def withTimeout(timeout: FiniteDuration)(
        implicit system: ActorSystem,
        ec: ExecutionContext): Future[T] = {
      Future.firstCompletedOf(
        future :: after(timeout, system.scheduler)(
          Future.failed(new TimeoutException)) :: Nil)
    }
  }

}
