package app.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import app.utils.FlowOps._

trait Handler[In0, E, Out0] {
  type Out1
  type In1 = Out1

  def decodingFlow: Flow[In0, Either[E, Out1], NotUsed]

  def persistingFlow: Flow[In1, Either[E, Out0], NotUsed]

  def decodingResultSink: Sink[Either[E, Out1], NotUsed]
  def persistingSink: Sink[Either[E, Out0], NotUsed]

  def decodingFlowDiverted: Flow[In0, Out1, NotUsed] =
    decodingFlow.divertLeft(to = decodingResultSink)

  def persistingFlowDiverted: Flow[In1, Out0, NotUsed] =
    persistingFlow.divertLeft(to = persistingSink)

  def process: Flow[In0, Out0, NotUsed] =
    decodingFlowDiverted
      .via(persistingFlowDiverted)
}
