package io.simplematter.mqtt.load.clients

import io.netty.handler.codec.mqtt.MqttQoS
import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.kotlin.mqtt.subscribeAwait
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.messages.MqttSubAckMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random


class MqttSubscribing(
    private val clientId: String,
    private val stats: SimulationStats,
    private val config: MqttLoadSimulatorConfig,
    private val mqttClient: MqttClient
) {

    private val subacks = ConcurrentHashMap<Int, MqttSubAckMessage>()

    fun attachMqttHandlers() {
        mqttClient.publishHandler {
            stats.messageRecieved()
            MqttMonitoringCounters.publishReceived.inc()
        }.subscribeCompletionHandler { suback ->
            subacks.put(suback.messageId(), suback)
            MqttMonitoringCounters.subackReceived.inc()
        }
    }

    suspend fun subscribe(pattern: String) {
        try {
            val subscribeTimestamp = System.currentTimeMillis()
            val packetId = mqttClient.subscribeAwait(pattern, MqttQoS.AT_MOST_ONCE.value())
            MqttMonitoringCounters.subscribeSent.inc()
            log.debug("Sent SUBSCRIBE {} to {}", clientId, pattern)

            withTimeout(config.mqtt.connectionTimeoutSeconds * 10 * 1000L) {
                do {
                    val suback = subacks.remove(packetId)
                    if (suback != null) {
                        if (suback.grantedQoSLevels().size != 1) {
                            throw IllegalArgumentException("Unexpected SUBACK items count: ${suback.grantedQoSLevels().size}")
                        }
                        if (suback.grantedQoSLevels().get(0) > 2) {
                            throw IllegalArgumentException(
                                "SUBACK with failure code: ${
                                    suback.grantedQoSLevels().get(0)
                                }"
                            )
                        }
                    } else {
                        delay(10)
                    }
                } while (suback == null)
            }

            val subscribeDuration = System.currentTimeMillis() - subscribeTimestamp
            MqttMonitoringCounters.subscribeLatency.observe(subscribeDuration.toDouble())
            if (subscribeDuration > 10000) {
                log.info("Subscribe ${clientId} to ${pattern} took ${subscribeDuration} ms")
            }

            stats.subscribedToTopic()
            MqttMonitoringCounters.subscriptionsCurrent.inc()
            log.debug("Subscribed {} to {}", clientId, pattern)
        } catch (e: Exception) {
            log.error("Subscribe ${clientId} to ${pattern} failed", e)
            MqttMonitoringCounters.subscribeFailed.inc()
        }
    }

    fun pickNewPattern(patterns: List<String>, existingPatterns: Set<String>): String? {
        if(existingPatterns.size < patterns.size) {
            for(i in 0..1000) {
                val pattern = patterns.get(random.nextInt(patterns.size))
                if(!existingPatterns.contains(pattern)) {
                    return pattern
                }
            }
            return null
        } else {
            return null
        }
    }


    companion object {
        private val log = LoggerFactory.getLogger(MqttSubscribing::class.java)

        private val random = Random(System.currentTimeMillis())
    }
}