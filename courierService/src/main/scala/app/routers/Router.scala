package app.routers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import app.model.CourierAvailability
import app.model.AddCourier
import app.model.Zone
import app.services.CourierService
import app.services.OrderService
import app.services.SnsPublisher
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.Endpoint
import sttp.tapir.endpoint
import sttp.tapir.path
import sttp.tapir.query
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.akkahttp.SwaggerAkka

import java.util.UUID
import scala.concurrent.ExecutionContext

class Router(courierService: CourierService, orderService: OrderService, snsPublisher: SnsPublisher)(
    implicit ec: ExecutionContext,
    m: Materializer
) {
  val enableCourier: Endpoint[(UUID, CourierAvailability), Unit, Boolean, Any] =
    endpoint.patch
      .in("courier")
      .in(path[UUID]("courierId"))
      .in(
        jsonBody[CourierAvailability]
          .description("Enable/disable a courier.")
          .example(CourierAvailability("John", Zone.N, available = true))
      )
      .out(jsonBody[Boolean])

  val orders: Endpoint[UUID, Unit, Seq[UUID], Any] =
    endpoint.get
      .in("orders")
      .in(query[UUID]("courierId"))
      .out(jsonBody[Seq[UUID]])

  val courierByOrder: Endpoint[UUID, Unit, UUID, Any] =
    endpoint.get
      .in("courier")
      .in(query[UUID]("orderId"))
      .out(jsonBody[UUID])

  val courierByOrderRoute: Route =
    AkkaHttpServerInterpreter().toRoute(courierByOrder) { orderId =>
      orderService
        .getCourier(orderId)
        .map(res => res.map(id => Right(id)).getOrElse(Left(s"$orderId not found")))
    }

  val ordersRoute: Route =
    AkkaHttpServerInterpreter().toRoute(orders) { courierId =>
      courierService
        .getOrders(courierId)
        .map(orders => orders.asRight[Unit])
    }

  val enableCourierRoute: Route = AkkaHttpServerInterpreter().toRoute(enableCourier) {
    case (courierId, availability) =>
      Source
        .future(
          courierService
            .changeAvailability(courierId, availability.available)
        )
        .map { _ =>
          AddCourier(courierId = courierId,
                     name = availability.name,
                     zone = availability.zone,
                     isAvailable = availability.available).asJson.noSpaces
        }
        .via(snsPublisher.publish())
        .run()
        .map(_ => true.asRight[Unit])
  }

  val openApiDocs: OpenAPI =
    OpenAPIDocsInterpreter().toOpenAPI(List(enableCourier, orders, courierByOrder),
                                       "The courier service",
                                       "1.0.0")
  val openApiYml: String = openApiDocs.toYaml

  def routers: Route = ordersRoute ~ enableCourierRoute ~ new SwaggerAkka(openApiYml).routes
}
