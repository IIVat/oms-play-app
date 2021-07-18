package app.service

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.alpakka.sns.scaladsl.SnsPublisher
import akka.stream.scaladsl.Flow
import app.config.AwsSettings
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishResponse

import java.net.URI

trait Publisher {
  def publish(): Flow[String, PublishResponse, NotUsed]
}

class SnsPublisher(settings: AwsSettings)(implicit val system: ActorSystem) extends Publisher {

  import settings._

  private implicit val mat: Materializer = Materializer.matFromSystem
  private implicit val ec                = system.dispatcher

  val credentials =
    AwsBasicCredentials.create("accesskey", "secretkey")

  private implicit val awsSnsClient: SnsAsyncClient =
    SnsAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .endpointOverride(URI.create(sns.url))
      .region(Region.of(region))
      .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
      .build()

  def publish(): Flow[String, PublishResponse, NotUsed] =
    SnsPublisher.flow(sns.topicArn)

  system.registerOnTermination(awsSnsClient.close())
}
