package app.itest

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.sns.scaladsl.SnsPublisher
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import cats.implicits.toBifunctorOps
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient
import sttp.client3.ResponseException
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.client3.circe.asJson

import java.net.URI
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.util.Try

object OrderManagerTestApp extends App with StrictLogging {
  val settings = TestConfig()

  logger.info(s"Starting Order Management Tess App with the following configuration: $settings")

  import settings._

  val credentials =
    AwsBasicCredentials.create("accesskey", "secretkey")

  implicit val sys: ActorSystem  = ActorSystem()
  implicit val mat: Materializer = Materializer.matFromSystem
  implicit val ec                = sys.dispatcher

  private implicit val awsSnsClient: SnsAsyncClient =
    SnsAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .endpointOverride(URI.create(snsSettings.url))
      .region(Region.of("eu-west-2"))
      .httpClient(AkkaHttpClient.builder().withActorSystem(sys).build())
      .build()

  val orderIdN1 = UUID.fromString("a49261aa-2907-498e-aa24-3bce25590a78")
  val orderIdN2 = UUID.fromString("b49261aa-2907-498e-aa24-3bce25590a78")
  val orderIdS1 = UUID.fromString("c49261aa-2907-498e-aa24-3bce25590a78")
  val orderIdS2 = UUID.fromString("e49261aa-2907-498e-aa24-3bce25590a78")

  val orderEvents = List(
    AddOrder(
      orderIdN1,
      "details",
      Zone.N,
      Instant.now()
    ),
    AddOrder(
      orderIdN2,
      "details",
      Zone.N,
      Instant.now()
    ),
    AddOrder(
      orderIdS1,
      "details",
      Zone.S,
      Instant.now()
    ),
    AddOrder(
      orderIdS2,
      "details",
      Zone.S,
      Instant.now()
    )
  ).map(_.asJson.noSpaces)

  val courierIdN = UUID.fromString("a59261aa-2907-498e-aa24-3bce25590a78")
  val courierIdS = UUID.fromString("b59261aa-2907-498e-aa24-3bce25590a78")

  val courierEvents = List(
    AddCourier(
      courierIdN,
      "John",
      Zone.N,
      true
    ),
    AddCourier(
      courierIdS,
      "Fred",
      Zone.S,
      true
    )
  ).map(_.asJson.noSpaces)

  def snsStream(events: List[String]) =
    Source
      .fromIterator(() => events.iterator)
      .delay(5.second)
      .via(SnsPublisher.flow(snsSettings.topicArn))
      .run()

  import sttp.client3._

  val backend = AkkaHttpBackend()

  def patch[A: BodySerializer, B: Decoder](
      path: String,
      req: A
  ) =
    basicRequest
      .patch(uri"$path")
      .body(req)
      .response(asJson[B])
      .send(backend)

  def get[B: Decoder](
      path: String
  ) =
    basicRequest
      .get(uri"$path")
      .response(asJson[B])
      .send(backend)

  val host = "http://localhost:9070"

  def getOrdersBy(courierId: UUID) =
    get[Seq[String]](s"$host/orders?courierId=${courierId.toString}").map(_.body)

  def getCourierBy(orderId: UUID) =
    get[String](s"$host/courier?orderId=${orderId.toString}").map(_.body)

  def makeAvailable(courierId: UUID, courierAvailability: CourierAvailability) =
    patch[CourierAvailability, Boolean](s"$host/courier/$courierId", courierAvailability)
      .map(_.body)

  import Ops._
  logger.info("Warming up...")

  Thread.sleep(10000)

  logger.info("Publishing events to SNS")

  for {
    _ <- snsStream(orderEvents ++ courierEvents)
  } yield {
    orderEvents.foreach(event => logger.info(s"Order event $event was published to SNS"))
    courierEvents.foreach(event => logger.info(s"Courier event $event was published to SNS"))
  }

  logger.info("It can take ~90 sec. Waiting ....")

  Thread.sleep(90000)

  logger.info("Trying to get results...")

  val getOrdersSouthBy    = getOrdersBy(courierIdS)
  val getOrdersNorthBy    = getOrdersBy(courierIdN)
  val getCourierSouthByO2 = getCourierBy(orderIdS1)
  val getCourierSouthByO1 = getCourierBy(orderIdS2)
  val getCourierNorthByO1 = getCourierBy(orderIdN1)
  val getCourierNorthByO2 = getCourierBy(orderIdN2)

  val resF = for {
    ordersS   <- getOrdersSouthBy
    courierS1 <- getCourierSouthByO1
    courierS2 <- getCourierSouthByO2
    ordersN   <- getOrdersNorthBy
    courierN1 <- getCourierNorthByO1
    courierN2 <- getCourierNorthByO2
  } yield {

    logger.info("should return 2 orders for south courier")
    ordersS.assertE(_.size == 2, "South Orders check failed")

    logger.info("should return south courier by order S1")
    courierS1.assertE(_ == courierIdS.toString, "South Courier check failed")
    logger.info("should return south courier order S2")
    courierS2.assertE(_ == courierIdS.toString, "South Courier check failed")

    logger.info("Should return 2 orders for north courier")
    ordersN.assertE(_.size == 2, "North Orders check failed")

    logger.info("should return north courier by order N1")
    courierN1.assertE(_ == courierIdN.toString, "North Courier check failed")
    logger.info("should return north courier order N2")
    courierN2.assertE(_ == courierIdN.toString, "North Courier check failed")
  }

  resF.foreach { _ =>
    sys.terminate()
    sys.registerOnTermination(awsSnsClient.close())
  }

  Await.result(resF, 30.seconds)

  System.exit(1)
}

object Ops {
  implicit class Assert[E, R](resp: Either[ResponseException[String, E], R]) extends StrictLogging {
    def assertE(p: R => Boolean, message: String): Unit = {
      resp
        .map { resp =>
          Try(assert(p(resp), message)).getOrElse {
            logger.error(message)
          }
        }
        .leftMap(err => logger.error(s"Request failed, ${err.getMessage}"))
    }
  }
}
