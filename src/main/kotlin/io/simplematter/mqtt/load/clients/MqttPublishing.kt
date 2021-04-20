package io.simplematter.mqtt.load.clients

import io.netty.handler.codec.mqtt.MqttQoS
import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.core.buffer.Buffer
import io.vertx.mqtt.MqttClient
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import kotlin.random.Random


class MqttPublishing(private val clientId: String,
                     private val topics: List<String>,
                     private val stats: SimulationStats,
                     private val config: MqttLoadSimulatorConfig,
                     private val mqttClient: MqttClient) {

    private val publishQoS = MqttQoS.valueOf(config.load.publishQos)

    fun sendRandomMessage() {
        val topic = topics.get(random.nextInt(topics.size))

        mqttClient.publish(topic, Buffer.buffer(randomMessageBody()), publishQoS, false, false)
        log.debug("Sent random message from {} to {}", clientId, topic)
        MqttMonitoringCounters.publishSent.inc()
        stats.messageSent()
    }

    private fun randomMessageBody(): String {
        val b = StringBuilder()
        val desiredLength = random.nextInt(config.load.messageMinSize, config.load.messageMaxSize)

        do {
            if(b.isNotEmpty())
                b.append(" ")
            b.append(randomWord())
        } while (b.length < desiredLength)

        return b.substring(0, Math.min(b.length, config.load.messageMaxSize))
    }

    companion object {
        private val log = LoggerFactory.getLogger(MqttPublishing::class.java)

        private val random = Random(System.currentTimeMillis())

        private fun randomWord(): String = messageParts.get(random.nextInt(messageParts.size))

        private val messageParts = listOf(
            "hello",
            "how",
            "bye",
            "thanks",
            "sensor",
            "data",
            "temperature",
            "speed",
            "altitude",
            "pressure",
            "degrees",
            "initialized",
            "ok",
            "error",
            "confirm",
            "reject",
            "open",
            "close")
    }
}