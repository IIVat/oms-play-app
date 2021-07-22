package app.services

import akka.NotUsed
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.scaladsl._
import app.models._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser.decode
import io.circe.generic.auto._
import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.ExecutionContext

class CourierHandler(courierService: OrderAssignmentService)(implicit ec: ExecutionContext)
    extends Handler[(Event, Message), ProcessingError, Message]
    with StrictLogging {

  override type Out1 = (AddCourier, Message)

  val decodingFlow
    : Flow[(Event, Message), Either[ProcessingError, (AddCourier, Message)], NotUsed] =
    Flow.fromFunction {
      case (event, msg) =>
        decode[AddCourier](event.content)
          .leftMap(error => DecodingError(error.getMessage))
          .map(_ -> msg)
    }

  val persistingFlow: Flow[(AddCourier, Message), Either[ProcessingError, Message], NotUsed] =
    Flow[(AddCourier, Message)]
      .mapAsync(4) {
        case (addCourier, msg) =>
          courierService.insertOrUpdate(addCourier).map {
            case 0L => Left(PersistenceError("Persistence error: Courier already exists"))
            case _  => Right(msg)
          }
      }

  override def decodingResultSink: Sink[Either[ProcessingError, (AddCourier, Message)], NotUsed] =
    Sink.ignore.mapMaterializedValue(_ => NotUsed)

  override def persistingSink: Sink[Either[ProcessingError, Message], NotUsed] = {
    Sink.ignore.mapMaterializedValue(_ => NotUsed)
  }

  val processFlow: Flow[(Event, Message), MessageAction, NotUsed] =
    process.map(MessageAction.delete)
}
