package app.services

import akka.NotUsed
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import app.model.DecodingError
import app.model.OrderAssignment
import app.model.PersistenceError
import app.model.ProcessingError
import app.utils.FlowOps.FlowEitherOps
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser
import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.ExecutionContext

trait Handler[In0, E, Out0] {
  type Out1
  type In1 = Out1

  def decodingFlow: Flow[In0, Either[E, Out1], NotUsed]

  def persistingFlow: Flow[In1, Either[E, Out0], NotUsed]

  def decodingResultSink: Sink[Either[E, Out1], NotUsed]
  def persistingSink: Sink[Either[E, Out0], NotUsed]

  def decodingFlowDiverted: Flow[In0, Out1, NotUsed] =
    decodingFlow.divertLeft(to = decodingResultSink)

  def persistingFlowDiverted: Flow[In1, Out0, NotUsed] =
    persistingFlow.divertLeft(to = persistingSink)

  def process: Flow[In0, Out0, NotUsed] =
    decodingFlowDiverted
      .via(persistingFlowDiverted)
}

class OrderAssignmentSubscriber(orderService: OrderService)(implicit ec: ExecutionContext)
    extends Handler[Message, ProcessingError, Message] {

  override type Out1 = (OrderAssignment, Message)

  val decodingFlow: Flow[Message, Either[ProcessingError, (OrderAssignment, Message)], NotUsed] =
    Flow.fromFunction { msg =>
      parser
        .decode[OrderAssignment](msg.body())
        .leftMap(error => DecodingError(error.getMessage))
        .map(_ -> msg)
    }

  val persistingFlow: Flow[(OrderAssignment, Message), Either[ProcessingError, Message], NotUsed] =
    Flow[(OrderAssignment, Message)]
      .mapAsync(4) {
        case (assignment, msg) =>
          orderService.addOrder(assignment).map {
            case 0L => Left(PersistenceError("Persistence error: Courier already exists"))
            case _  => Right(msg)
          }
      }

  override def decodingResultSink
    : Sink[Either[ProcessingError, (OrderAssignment, Message)], NotUsed] =
    Sink.ignore.mapMaterializedValue(_ => NotUsed)

  override def persistingSink: Sink[Either[ProcessingError, Message], NotUsed] = {
    Sink.ignore.mapMaterializedValue(_ => NotUsed)
  }

  val processFlow: Flow[Message, MessageAction, NotUsed] =
    process.map(MessageAction.delete)
}
