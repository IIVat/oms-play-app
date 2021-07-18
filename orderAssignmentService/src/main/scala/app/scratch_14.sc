import app.model.{AddCourier, DecodingError, Event, Zone}
import io.circe.config.parser.decode
import io.circe.{Decoder, Encoder, parser}

import java.util.UUID
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps

import java.time.Instant

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

import Zone._

final case class AddCourier(courierId: UUID, name: String, zone: Zone, isAvailable: Boolean)

parser.parse(Event(AddCourier(UUID.randomUUID(), "first_courirer", N, true).asJson.noSpaces).asJson.noSpaces)



//{"content" : "{\"courierId\":\"e87261aa-2907-498e-aa24-3bce25590a46\",\"name\":\"first_courirer\",\"zone\":\"N\",\"isAvailable\":true}"}

val m = Event("""{"courierId":"e87261aa-2907-498e-aa24-3bce25590a46","name":"first_courirer","zone":"N","isAvailable":true}""")

decode[AddCourier](m.content)

//{"content" : "{"courierId":"e87261aa-2907-498e-aa24-3bce25590a46","name":"first_courirer","zone":"N","isAvailable":true}"}
final case class AddOrder(orderId: UUID, details: String, zone: Zone, addedAt: Instant)

parser.parse(Event(AddOrder(UUID.randomUUID(), "first_courirer", N, Instant.now).asJson.noSpaces).asJson.noSpaces)