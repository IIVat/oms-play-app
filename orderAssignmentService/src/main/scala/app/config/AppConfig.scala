package app.config

import software.amazon.awssdk.regions.Region

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

case class AppConfig(server: ServerSettings, aws: AwsSettings, redis: RedisSettings)

case class AwsSettings(region: String, eventsQueue: SqsStreamSettings, sns: SnsSettings) {
  lazy val awsRegion: Region = Region.of(region)
}

case class ServerSettings(host: String, port: Int)

case class SqsSourceSettings(url: String,
                             bufferSize: Int,
                             visibilityTimeout: FiniteDuration,
                             waitTime: FiniteDuration)

case class SnsSettings(url: String, topicArn: String)

case class RedisSettings(url: String, port: Int)

case class SqsStreamSettings(
    sqs: SqsSourceSettings,
    sqsAck: GroupedSqsAckSettings = GroupedSqsAckSettings(),
    stream: StreamSettings = StreamSettings(),
    restart: RestartSettings = RestartSettings()
)

case class GroupedSqsAckSettings(
    maxBatchSize: Int = 10,
    maxBatchWait: FiniteDuration = 500.millis,
    concurrentRequests: Int = 1
)

case class StreamSettings(parallelism: Int = 4)

case class RestartSettings(
    minBackoff: FiniteDuration = 3.seconds,
    maxBackoff: FiniteDuration = 30.seconds,
    randomFactor: Double = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxRestarts: Int = 20 // limits the amount of restarts to 20
)
