package app

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorAttributes
import akka.stream.KillSwitches
import akka.stream.RestartSettings
import akka.stream.Supervision
import akka.stream.alpakka.sqs.SqsAckGroupedSettings
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.sqs.scaladsl.SqsAckFlow
import akka.stream.alpakka.sqs.scaladsl.SqsSource
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import app.config.AppConfig
import app.db.RedisCourierDao
import app.model.DecodingError
import app.model.Event
import app.model.ProcessingError
import app.model.SqsMessage
import app.service.CourierHandler
import app.service.CourierService
import app.service.OrderAssignmentHandler
import app.service.SnsPublisher
import app.utils.FlowOps._
import cats.effect.IO
import cats.implicits._
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.typesafe.scalalogging.StrictLogging
import io.circe
import io.circe.Json
import io.circe.config.parser
import io.circe.generic.auto._
import redis.RedisClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

import java.net.URI
import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal

object WebServer extends StrictLogging {
  def main(args: Array[String]): Unit = {
    import io.circe.config.syntax._

    val settings = parser.decodeF[IO, AppConfig]().unsafeRunSync()

    logger.info(s"Starting application with the following configuration: $settings")

    implicit val system = ActorSystem("events-system")

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val credentials =
      AwsBasicCredentials.create("accesskey", "secretkey")

    implicit val awsSqsClient: SqsAsyncClient = SqsAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .endpointOverride(URI.create(settings.aws.eventsQueue.sqs.url))
      .region(settings.aws.awsRegion)
      .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
      .build()

    //todo replace into separate file
    val courierDao = new RedisCourierDao(
      RedisClient(host = settings.redis.url, settings.redis.port)
    )
    val courierService = new CourierService(courierDao)
    val snsPublisher   = new SnsPublisher(settings.aws)

    val courierHandler         = new CourierHandler(courierService)
    val orderAssignmentHandler = new OrderAssignmentHandler(courierService, snsPublisher)

    val decider: Supervision.Decider = {
      case NonFatal(e) =>
        logger.error("Error in the Product Sync Flow", e)
        Supervision.Resume
      case fatal =>
        logger.error("Fatal error in the Product Sync Flow", fatal.getCause)
        Supervision.Stop
    }

    val sourceSettings = SqsSourceSettings()
      .withMaxBufferSize(settings.aws.eventsQueue.sqs.bufferSize)
      .withVisibilityTimeout(settings.aws.eventsQueue.sqs.visibilityTimeout)
      .withWaitTime(settings.aws.eventsQueue.sqs.waitTime)

    val ackFlow = SqsAckFlow
      .grouped(settings.aws.eventsQueue.sqs.url, SqsAckGroupedSettings())
      .withAttributes(ActorAttributes.supervisionStrategy(decider))

    import settings.aws.eventsQueue.restart._
    //would be great to use source with restart
    val source = RestartSource.withBackoff(
      RestartSettings(minBackoff = minBackoff, maxBackoff = maxBackoff, randomFactor = randomFactor)
        .withMaxRestarts(maxRestarts, minBackoff)
    )(() => SqsSource(settings.aws.eventsQueue.sqs.url, sourceSettings))

    def decodeMessage(message: Message): Either[circe.Error, (Event, Message)] = {
      val sqsMessage = io.circe.parser
        .decode[SqsMessage](io.circe.parser.parse(message.body).getOrElse(Json.Null).noSpaces)
      sqsMessage.map { m =>
        (Event(m.Message), message)
      }
    }

    val decodingFlow: Flow[Message, Either[ProcessingError, (Event, Message)], NotUsed] =
      Flow.fromFunction(
        msg => decodeMessage(msg).leftMap(error => DecodingError(error.getMessage))
      )

    val decodingFlowDiverted: Flow[Message, (Event, Message), NotUsed] =
      decodingFlow.divertLeft(to = Sink.onComplete(x => x.getOrElse("Error")))

    val route =
      path("status") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, """{"status": "ok"}"""))
        }
      }

    val bindingFuture = Http()
      .newServerAt(settings.server.host, settings.server.port)
      .bind(route)
      .foreach(
        _ => logger.info(s"Http server started on ${settings.server.host}: ${settings.server.port}")
      )

    val flow = source
      .via(decodingFlowDiverted)
      .alsoTo(courierHandler.processFlow.via(ackFlow).to(Sink.ignore))
      .via(orderAssignmentHandler.processFlow.via(ackFlow))
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()

    system.registerOnTermination(flow.shutdown())
    system.registerOnTermination(awsSqsClient.close())
  }
}
