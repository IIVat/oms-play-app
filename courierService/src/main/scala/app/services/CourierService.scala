package app.services

import app.db.CourierRepository

import java.util.UUID
import scala.concurrent.Future

trait CourierService {
  def getOrders(courierId: UUID): Future[Seq[UUID]]
  def changeAvailability(courierId: UUID, enable: Boolean): Future[Boolean]
}

class CourierServiceImpl(courierRepo: CourierRepository) extends CourierService {

  override def getOrders(courierId: UUID): Future[Seq[UUID]] = courierRepo.getOrders(courierId)

  override def changeAvailability(courierId: UUID, enable: Boolean): Future[Boolean] =
    courierRepo.changeAvailability(courierId, enable)
}
