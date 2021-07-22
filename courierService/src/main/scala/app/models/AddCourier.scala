package app.models

import java.util.UUID

final case class AddCourier(courierId: UUID, name: String, zone: Zone, isAvailable: Boolean)
