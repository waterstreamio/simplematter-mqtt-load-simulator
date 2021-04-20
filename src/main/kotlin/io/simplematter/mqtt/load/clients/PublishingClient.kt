package io.simplematter.mqtt.load.clients

import io.netty.handler.codec.mqtt.MqttQoS
import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.core.Future
import io.vertx.core.Vertx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


class PublishingClient(
    private val clientId: String,
    private val topics: List<String>,
    private val stats: SimulationStats,
    private val config: MqttLoadSimulatorConfig,
    private val vertx: Vertx,
    private val parentJob: Job,
    private val rampUpComplete: Future<Void>
):  AbstractClient(clientId, config, vertx, parentJob, rampUpComplete) {

    private val publishing = MqttPublishing(clientId, topics, stats, config, mqttClient)

    override fun start() {
        super.start()

        val msBetweenMessages: Long = (1000.0 / config.load.publishingClients.messagesPerSecond).toLong()

        launch {
            var lastTick = System.currentTimeMillis()
            while (!isStopping()) {
                if (canDoActions()) {
                    val timeToWait = Math.max(msBetweenMessages - (System.currentTimeMillis() - lastTick), 0)
                    delay(timeToWait)
                    publishing.sendRandomMessage()
                    lastTick = System.currentTimeMillis()
                } else {
                    delay(config.load.simulationStepInterval)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishingClient::class.java)
    }
}