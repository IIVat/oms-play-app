package app.model

import java.util.UUID

final case class Courier(courierId: UUID, name: String, zone: Zone, isAvailable: Boolean)
