package app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.alpakka.sqs.SqsAckGroupedSettings
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.sqs.scaladsl.SqsAckFlow
import akka.stream.alpakka.sqs.scaladsl.SqsSource
import akka.stream.ActorAttributes
import akka.stream.KillSwitches
import akka.stream.RestartSettings
import akka.stream.Supervision
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import app.config.AppConfig
import app.services.OrderAssignmentSubscriber
import cats.effect.IO
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.typesafe.scalalogging.StrictLogging
import io.circe.config.parser
import io.circe.generic.auto._
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.sqs.SqsAsyncClient

import java.net.URI
import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal

object WebServer extends App with StrictLogging {
  import io.circe.config.syntax._

  val settings = parser.decodeF[IO, AppConfig]().unsafeRunSync()

  logger.info(s"Starting application with the following configuration: $settings")

  implicit val system = ActorSystem("courier-service-system")

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

  val decider: Supervision.Decider = {
    case NonFatal(e) =>
      logger.error("Error in the Order Assignment Flow", e)
      Supervision.Resume
    case fatal =>
      logger.error("Fatal error in the Order Assignment Flow", fatal.getCause)
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
  val source = RestartSource.withBackoff(
    RestartSettings(minBackoff = minBackoff, maxBackoff = maxBackoff, randomFactor = randomFactor)
      .withMaxRestarts(maxRestarts, minBackoff)
  )(() => SqsSource(settings.aws.eventsQueue.sqs.url, sourceSettings))

  val interpreter = new Interpreter(settings)

  val mainFlow = source
    .via(interpreter.assignmentSubscriber.processFlow)
    .via(ackFlow)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(Sink.ignore)(Keep.left)
    .withAttributes(ActorAttributes.supervisionStrategy(decider))
    .run()

  val bindingFuture = Http()
    .newServerAt(settings.server.host, settings.server.port)
    .bind(interpreter.router.routers)
    .foreach(
      _ => logger.info(s"Http server started on ${settings.server.host}: ${settings.server.port}")
    )
}
