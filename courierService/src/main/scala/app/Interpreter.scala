package app

import akka.actor.ActorSystem
import app.config.AppConfig
import app.db.CourierRepositoryImpl
import app.db.OrderRepositoryImpl
import app.routers.Router
import app.services.CourierServiceImpl
import app.services.OrderAssignmentSubscriber
import app.services.OrderServiceImpl
import app.services.SnsPublisher
import redis.RedisClient

class Interpreter(settings: AppConfig)(implicit sys: ActorSystem) {
  implicit val ec          = sys.dispatcher
  val redisClient          = RedisClient(host = settings.redis.url, port = settings.redis.port)
  val orderRepo            = new OrderRepositoryImpl(redisClient)
  val orderService         = new OrderServiceImpl(orderRepo)
  val courierRepo          = new CourierRepositoryImpl(redisClient)
  val courierService       = new CourierServiceImpl(courierRepo)
  val snsPublisher         = new SnsPublisher(settings.aws)
  val assignmentSubscriber = new OrderAssignmentSubscriber(orderService)
  val router: Router       = new Router(courierService, orderService, snsPublisher)
}
