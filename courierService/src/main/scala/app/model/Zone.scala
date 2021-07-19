package app.model

import io.circe.Decoder
import io.circe.Encoder

sealed abstract class Zone(val value: String)

object Zone {
  final case object N extends Zone("N")
  final case object S extends Zone("S")
  final case object E extends Zone("E")
  final case object W extends Zone("W")

  implicit val decodeMode: Decoder[Zone] = Decoder[String].emap {
    case N.value => Right(N)
    case S.value => Right(S)
    case E.value => Right(E)
    case W.value => Right(W)
    case other   => Left(s"Invalid mode: $other")
  }

  implicit val encodeMode: Encoder[Zone] = Encoder[String].contramap {
    case N => N.value
    case S => S.value
    case E => E.value
    case W => W.value
  }
}
