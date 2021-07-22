package app.itest

final case class TestConfig(snsSettings: SnsSettings = SnsSettings(),
                            client: ClientSettings = ClientSettings())

final case class SnsSettings(url: String = "http://localhost:9911",
                             topicArn: String = "arn:aws:sns:eu-west-2:123450000001:events-topic")

final case class ClientSettings(url: String = "http://localhost:9070")
