package co.horn.boris.utils

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge}

object StreamUtils {

  /**
    * A Flow that balance and distribute the stream among some workers
    * @param workers Sequence of flows that will execute the given work
    */
  def balancer[In, Out](workers: Seq[Flow[In, Out, Any]]): Flow[In, Out, NotUsed] = {
    import GraphDSL.Implicits._

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      val balancer = b.add(Balance[In](workers.size))
      val merge = b.add(Merge[Out](workers.size))

      workers.map { worker ⇒
        balancer ~> worker ~> merge
      }

      FlowShape(balancer.in, merge.out)
    })
  }
}
