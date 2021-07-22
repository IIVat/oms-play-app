package app.db

import redis.RedisClient

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait OrderRepository {
  def addOrder(orderId: UUID, courierId: UUID): Future[Long]
  def getCourier(orderId: UUID): Future[Option[UUID]]
}

class OrderRepositoryImpl(redisClient: RedisClient)(implicit ec: ExecutionContext)
    extends OrderRepository {

  override def addOrder(orderId: UUID, courierId: UUID): Future[Long] = {
    val tx = redisClient.multi()
    tx.exec()
    tx.hset(orderId.toString, "courierId", courierId.toString)
    val result = tx.sadd(courierId.toString, orderId.toString)
    tx.exec()
    result
  }

  override def getCourier(orderId: UUID): Future[Option[UUID]] = {
    redisClient
      .hget[String](orderId.toString, "courierId")
      .map(_.map(UUID.fromString))
  }
}
