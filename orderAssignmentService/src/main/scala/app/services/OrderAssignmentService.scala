package app.services

import app.db.CourierZoneRepository
import app.db.OrderAssignmentRepository
import app.models.AddCourier
import app.models.AddOrder
import app.models.Courier
import app.models.OrderAssignment
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.implicits._

class OrderAssignmentService(courierRepo: CourierZoneRepository,
                             orderAssignmentRepo: OrderAssignmentRepository)
    extends StrictLogging {
  def insertOrUpdate(addCourier: AddCourier): Future[Long] = {
    val courier = Courier(addCourier.courierId, addCourier.zone, addCourier.isAvailable)
    courierRepo.isExist(courier.courierId).flatMap {
      case true if !courier.isAvailable =>
        courierRepo.delete(courier.zone, courier.courierId).map { res =>
          logger.info(s"The courier [id: ${courier.courierId}] was deactivated")
          res
        }
      case false if courier.isAvailable =>
        courierRepo.add(courier).map { res =>
          logger.info(s"The courier [${addCourier.courierId}] is available now!")
          res
        }
      case _ =>
        logger.error(s"Invalid state. Ignore action for the courier [id: ${courier.courierId}].")
        Future.successful(0) // must be sent meaningful message: CourierAlreadyExists
    }
  }

  def checkAndAssign(addOrder: AddOrder): Future[Option[OrderAssignment]] = {
    import addOrder._
    orderAssignmentRepo.isAssigned(orderId).flatMap {
      case true =>
        logger.error(s"The order [id: $orderId] is already assigned")
        Future.successful(None)
      case false =>
        courierRepo.getAndIncr(zone).flatMap { courierIdOpt =>
          courierIdOpt.flatTraverse { courierId =>
            orderAssignmentRepo
              .assign(OrderAssignment(orderId, courierId))
              .map {
                case true =>
                  logger.info(s"The order [id: $orderId] is assigned to a courier [id: $courierId]")
                  Some(OrderAssignment(orderId, courierId))
                case _ =>
                  logger.error(
                    s"The order [id: $orderId] was not assigned to a courier [id: $courierId]"
                  )
                  None
              }
          }
        }
    }
  }
}
