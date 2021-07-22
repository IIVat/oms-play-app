package app.models

import java.util.UUID

final case class Courier(courierId: UUID, zone: Zone, isAvailable: Boolean, score: Long = 0L)
