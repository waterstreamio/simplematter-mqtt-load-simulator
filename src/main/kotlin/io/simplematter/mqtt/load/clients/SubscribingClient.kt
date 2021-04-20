package io.simplematter.mqtt.load.clients

import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.utils.RandomUtils
import io.vertx.core.Future
import io.vertx.core.Vertx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


class SubscribingClient(
    private val clientId: String,
    private val topics: List<String>,
    private val topicGroupPatterns: List<String>,
    private val stats: SimulationStats,
    private val config: MqttLoadSimulatorConfig,
    private val vertx: Vertx,
    private val parentJob: Job,
    private val rampUpComplete: Future<Void>
):  AbstractClient(clientId, config, vertx, parentJob, rampUpComplete) {

    private val subscribing = MqttSubscribing(clientId, stats, config, mqttClient)

    private val regularSubscriptions = RandomUtils.pickRandomElements(topics, config.load.subscribingClients.regularSubscriptionsPerClient)

    private val wildcardSubscriptions = RandomUtils.pickRandomElements(topicGroupPatterns, config.load.subscribingClients.wildcardSubscriptionPerClient)

    suspend private fun subscribeToPatterns(patterns: List<String>) {
        for(pattern in patterns) {
            while(!canDoActions()) {
                delay(config.load.subscribingClients.delayBetweenSubscriptions)
            }
            subscribing.subscribe(pattern)
            delay(config.load.subscribingClients.delayBetweenSubscriptions)
        }
    }

    override fun start() {
        super.start()

        subscribing.attachMqttHandlers()

        launch {
            subscribeToPatterns(regularSubscriptions)
            log.info("Client {} finished {} regular subscriptions", clientId, regularSubscriptions.size)
            subscribeToPatterns(wildcardSubscriptions)
            log.info("Client {} finished {} wildcard subscriptions", clientId, wildcardSubscriptions.size)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SubscribingClient::class.java)
    }
}