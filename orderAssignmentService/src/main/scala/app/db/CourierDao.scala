package app.db

import app.model.Courier
import app.model.Zone
import redis.RedisClient
import cats.implicits._
import redis.api.ZaddOption

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure

trait CourierDao {
  def add(courier: Courier): Future[Long]
  def getAndIncr(zone: Zone): Future[Option[UUID]]
  def delete(zone: Zone, id: UUID): Future[Long]
}

class RedisCourierDao(redisClient: RedisClient)(implicit ec: ExecutionContext) extends CourierDao {
  override def add(courier: Courier): Future[Long] = {
    redisClient.zaddWithOptions(courier.zone.value,
                                Seq(ZaddOption.NX),
                                (courier.score.toDouble, courier.courierId.toString))
  }

  override def getAndIncr(zone: Zone): Future[Option[UUID]] = {
    val tx = redisClient.transaction()
    tx.exec()
    tx.watch(zone.value)
    val range = tx.zrange[String](zone.value, 0, 0).map(_.headOption)
    range.map(_.traverse(id => tx.zincrby(zone.value, 1.toDouble, id)))
    tx.exec()
    range.map(_.map(UUID.fromString))
  }

  override def delete(zone: Zone, id: UUID): Future[Long] = {
    redisClient.zrem(zone.value, id.toString)
  }
}
