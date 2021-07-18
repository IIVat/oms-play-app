package app

import scala.concurrent.Future

trait EventsConsumer {
  def consume: Future[Int]
}

object EventsConsumer {
  def consumer = new EventsConsumer {
    override def consume: Future[Int] = ???
  }
}
