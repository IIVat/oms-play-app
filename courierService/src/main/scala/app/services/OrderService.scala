package app.services

import app.db.OrderRepository
import app.model.OrderAssignment

import java.util.UUID
import scala.concurrent.Future

trait OrderService {
  def addOrder(assignment: OrderAssignment): Future[Long]
  def getCourier(orderId: UUID): Future[Option[UUID]]
}

class OrderServiceImpl(orderRepo: OrderRepository) extends OrderService {
  override def addOrder(assignment: OrderAssignment): Future[Long] =
    orderRepo.addOrder(assignment.orderId, assignment.courierId)

  override def getCourier(orderId: UUID): Future[Option[UUID]] = orderRepo.getCourier(orderId)
}
