package app.service

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import app.model._
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.services.sns.model.PublishResponse
import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.ExecutionContext

class OrderAssignmentHandler(courierService: CourierService, snsPublisher: Publisher)(
    implicit ec: ExecutionContext
) extends Handler[(Event, Message), ProcessingError, (OrderAssignment, Message)] {
  override type Out1 = (AddOrder, Message)

  val decodingFlow: Flow[(Event, Message), Either[ProcessingError, (AddOrder, Message)], NotUsed] =
    Flow.fromFunction {
      case (event, msg) =>
        decode[AddOrder](event.content)
          .leftMap(error => DecodingError(error.getMessage))
          .map(_ -> msg)
    }

  val persistingFlow
    : Flow[(AddOrder, Message), Either[ProcessingError, (OrderAssignment, Message)], NotUsed] =
    Flow[(AddOrder, Message)]
    //TODO extract 4 into config
      .mapAsync(4) {
        case (addOrder, msg) =>
          courierService
            .getAndIncr(addOrder.zone)
            .map { x =>
              x.map(courierId => (OrderAssignment(addOrder.orderId, courierId), msg))
                .toRight(PersistenceError("Couriers is not available. Wait in queue."))
            }
      }

  val processFlow: Flow[(Event, Message), MessageAction, NotUsed] = {
    //sends OrderAssignment to SNS and keep SQS Message for acknowledging SQS to delete
    //the message if sns successfully consumes assignment
    val graph = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val unzip = b.add(Unzip[String, Message]())
      val zip   = b.add(Zip[PublishResponse, Message]())

      unzip.out0 ~> snsPublisher.publish() ~> zip.in0
      unzip.out1 ~> zip.in1
      FlowShape(unzip.in, zip.out)
    })

    process
      .map { case (orderAssignment, msg) => orderAssignment.asJson.noSpaces -> msg }
      .via(graph)
      .collect {
        case (snsResp, msg) if snsResp.sdkHttpResponse().isSuccessful => MessageAction.delete(msg)
      }
  }
  //here can be handled decoding errors
  override def decodingResultSink: Sink[Either[ProcessingError, (AddOrder, Message)], NotUsed] =
    Sink.ignore.mapMaterializedValue(_ => NotUsed)

  //here can be handled persistence errors
  override def persistingSink: Sink[Either[ProcessingError, (OrderAssignment, Message)], NotUsed] =
    Sink.ignore.mapMaterializedValue(_ => NotUsed)
}
