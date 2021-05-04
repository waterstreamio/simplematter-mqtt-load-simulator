package io.simplematter.mqtt.load.clients

import io.netty.handler.codec.mqtt.MqttQoS
import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.kotlin.mqtt.subscribeAwait
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import io.vertx.mqtt.messages.MqttSubAckMessage
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.random.Random


class RandomizedClient(
    private val clientId: String,
    private val topics: List<String>,
    private val stats: SimulationStats,
    private val config: MqttLoadSimulatorConfig,
    private val vertx: Vertx,
    private val parentJob: Job,
    private val rampUpComplete: Future<Void>
):  AbstractClient(clientId, config, vertx, parentJob, rampUpComplete) {

    private val publishing = MqttPublishing(clientId, topics, stats, config, mqttClient)

    private val subscribing = MqttSubscribing(clientId, stats, config, mqttClient)

    private val subscriptions = mutableSetOf<String>()

    init {
        job.invokeOnCompletion {
            MqttMonitoringCounters.subscriptionsCurrent.dec(subscriptions.size.toDouble())
        }
    }

    private fun startActionLoop() {
        launch {
            while (!isStopping()) {
                val action = nextAction()

                if (!canDoActions()) {
                    //noop
                } else if (action == ClientAction.PUBLISH && topics.isNotEmpty()) {
                    publishing.sendRandomMessage()
                } else if (action == ClientAction.SUBSCRIBE && subscriptions.size < topics.size) {
                    subscribeRandomTopic()
                } else if (action == ClientAction.UNSUBSCRIBE && subscriptions.size > 0) {
                    unsubscribeRandomTopic()
                }
                delay(config.load.randomizedClients.clientStepInterval)
            }
        }
    }

    override fun start() {
        super.start()

        MqttMonitoringCounters.randomizedClientsCurrent.inc()
        job.invokeOnCompletion {
            MqttMonitoringCounters.randomizedClientsCurrent.dec()
        }

        subscribing.attachMqttHandlers()
        startActionLoop()
    }

    override fun onClientConnect(latency: Long) {
        super.onClientConnect(latency)
        MqttMonitoringCounters.subscriptionsDisconnectedCurrent.dec(subscriptions.size.toDouble())
    }

    override fun onMqttClientClose() {
        super.onMqttClientClose()
        MqttMonitoringCounters.subscriptionsDisconnectedCurrent.inc(subscriptions.size.toDouble())
    }


    private suspend fun subscribeRandomTopic() {
        if(subscriptions.size < config.load.randomizedClients.maxSubscriptionsPerClient) {
            val topic = subscribing.pickNewPattern(topics, subscriptions)
            if(topic != null) {
                subscribing.subscribe(topic)
                subscriptions.add(topic)
            } else {
                log.warn("Unable to pick a topic for subscription for ${clientId}")
            }
        }
    }

    private fun unsubscribeRandomTopic() {
        if(subscriptions.size > config.load.randomizedClients.maxSubscriptionsPerClient) {
            val topic = subscriptions.random(random)
            mqttClient.unsubscribe(topic)
            log.debug("Unsubscribed {} from {}", clientId, topic)
            subscriptions.remove(topic)
            stats.unsubscribedFromTopic()
            MqttMonitoringCounters.subscriptionsCurrent.dec()
            MqttMonitoringCounters.unsubscribeSent.inc()
        }
    }

    private fun nextAction(): ClientAction {
        val p = config.load.randomizedClients.clientActionProbabilities

        val n = random.nextInt(p.sum)
        return if (n < p.publishRange)
            ClientAction.PUBLISH
        else if (n < p.subscribeRange)
            ClientAction.SUBSCRIBE
        else if (n < p.unsubscribeRange)
            ClientAction.UNSUBSCRIBE
        else
            ClientAction.IDLE
    }

    private enum class ClientAction {
        PUBLISH, SUBSCRIBE, UNSUBSCRIBE, IDLE
    }

    companion object {
        private val log = LoggerFactory.getLogger(RandomizedClient::class.java)

        private val random = Random(System.currentTimeMillis())
    }
}