package app

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import sttp.client3.BodySerializer
import sttp.client3.StringBody
import sttp.model.MediaType

import java.time.Instant
import java.util.UUID

package object itest {
  final case class AddOrder(orderId: UUID, details: String, zone: Zone, addedAt: Instant)

  final case class AddCourier(courierId: UUID, name: String, zone: Zone, isAvailable: Boolean)

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

  final case class Id(value: String)

  final case class CourierAvailability(name: String, zone: Zone, available: Boolean)

  object CourierAvailability {
    implicit val serializer: BodySerializer[CourierAvailability] = { c: CourierAvailability =>
      val serialized = c.asJson.noSpaces
      StringBody(serialized, "UTF-8", MediaType.ApplicationJson)
    }
  }

}
