package app.db

import app.models.Courier
import app.models.OrderAssignment
import app.models.Zone
import redis.RedisClient

import java.util.UUID
import scala.concurrent.Future

trait OrderAssignmentRepository {
  def assign(oa: OrderAssignment): Future[Boolean]
  def isAssigned(orderId: UUID): Future[Boolean]
}

class OrderAssignmentRepositoryImpl(redisClient: RedisClient) extends OrderAssignmentRepository {
  def assign(oa: OrderAssignment): Future[Boolean] = {
    redisClient.hset(oa.orderId.toString, "courierId", oa.courierId.toString)
  }
  def isAssigned(orderId: UUID): Future[Boolean] = {
    redisClient.hexists(orderId.toString, "courierId")
  }
}
