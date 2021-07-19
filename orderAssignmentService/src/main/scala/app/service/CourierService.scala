package app.service

import app.db.CourierRepository
import app.model.AddCourier
import app.model.Courier
import app.model.Zone

import java.util.UUID
import scala.concurrent.Future

class CourierService(courierDao: CourierRepository) {
  def insertOrUpdate(addCourier: AddCourier): Future[Long] = {
    val courier = Courier(addCourier.courierId, addCourier.zone, addCourier.isAvailable)
    if (courier.isAvailable) {
      courierDao.add(courier)
    } else {
      courierDao.delete(courier.zone, courier.courierId)
    }
  }

  def getAndIncr(zone: Zone): Future[Option[UUID]] = {
    courierDao.getAndIncr(zone)
  }
}
