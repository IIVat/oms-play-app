package app.model

sealed trait ProcessingError
case class DecodingError(msg: String)    extends ProcessingError
case class ValidationError(msg: String)  extends ProcessingError
case class PersistenceError(msg: String) extends ProcessingError
