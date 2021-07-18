package app.model

import java.time.Instant
import java.util.UUID

case class Order(orderId: UUID, details: String, zone: Zone, addedAt: Instant)
