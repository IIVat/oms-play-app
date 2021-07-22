package app.db

import redis.RedisClient

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait CourierRepository {
  def changeAvailability(courierId: UUID, enable: Boolean): Future[Boolean]
  def getOrders(courierId: UUID): Future[Seq[UUID]]

}

class CourierRepositoryImpl(redisClient: RedisClient)(implicit ec: ExecutionContext)
    extends CourierRepository {

  override def changeAvailability(courierId: UUID, enable: Boolean): Future[Boolean] = {
    redisClient.hset(s"a_$courierId", "available", enable.toString)
  }

  override def getOrders(courierId: UUID): Future[Seq[UUID]] = {
    redisClient
      .smembers[String](courierId.toString)
      .map(_.map(UUID.fromString))
  }
}
