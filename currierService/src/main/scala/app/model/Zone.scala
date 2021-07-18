package app.model

sealed abstract class Zone(val value: Char)

object Zone {
  final case object N extends Zone('N')
  final case object S extends Zone('S')
  final case object E extends Zone('E')
  final case object W extends Zone('W')
}
