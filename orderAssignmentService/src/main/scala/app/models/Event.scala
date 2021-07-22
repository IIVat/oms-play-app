package app.models

import java.time.Instant
import java.util.UUID

case class Event(content: String)

final case class AddOrder(orderId: UUID, details: String, zone: Zone, addedAt: Instant)

final case class AddCourier(courierId: UUID, name: String, zone: Zone, isAvailable: Boolean)
