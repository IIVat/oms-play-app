package app.db

import app.models.Courier
import app.models.Zone
import redis.RedisClient
import cats.implicits._
import redis.api.ZaddOption

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure

trait CourierZoneRepository {
  def add(courier: Courier): Future[Long]
  def getAndIncr(zone: Zone): Future[Option[UUID]]
  def delete(zone: Zone, id: UUID): Future[Long]
  def isExist(id: UUID): Future[Boolean]
}

class CourierZoneRepositoryImpl(redisClient: RedisClient)(implicit ec: ExecutionContext)
    extends CourierZoneRepository {
  override def add(courier: Courier): Future[Long] = {
    val tx = redisClient.transaction()
    tx.exec()
    tx.set(courier.courierId.toString, "")
    val res = tx.zaddWithOptions(courier.zone.value,
                                 Seq(ZaddOption.NX),
                                 (courier.score.toDouble, courier.courierId.toString))
    tx.exec()
    res
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

  override def delete(zone: Zone, courierId: UUID): Future[Long] = {
    val tx = redisClient.transaction()
    tx.exec()
    tx.zrem(zone.value, courierId.toString: _*)
    val res = tx.del(courierId.toString)
    tx.exec()
    res
  }

  override def isExist(courierId: UUID): Future[Boolean] = {
    redisClient.exists(courierId.toString)
  }
}
