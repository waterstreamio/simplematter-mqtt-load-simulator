package io.simplematter.mqtt.load.clients

import io.simplematter.mqtt.load.SimulationStats
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.simplematter.mqtt.load.utils.RandomUtils
import io.vertx.core.Future
import io.vertx.core.Vertx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


class SubscribingClient(
    private val clientId: String,
    private val intermittent: Boolean,
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

    private var connectTimestamp: Long = 0
    private var disconnectTimestamp: Long = 0

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

        MqttMonitoringCounters.subscribingClientsCurrent.inc()
        job.invokeOnCompletion {
            MqttMonitoringCounters.subscribingClientsCurrent.dec()
        }

        subscribing.attachMqttHandlers()

        launch {
            subscribeToPatterns(regularSubscriptions)
            log.info("Client {} finished {} regular subscriptions", clientId, regularSubscriptions.size)
            subscribeToPatterns(wildcardSubscriptions)
            log.info("Client {} finished {} wildcard subscriptions", clientId, wildcardSubscriptions.size)
        }

        if(intermittent) {
            launch {
                while (!isStopping()) {
                    if (mqttClient.isConnected) {
                        val uptime = System.currentTimeMillis() - connectTimestamp
                        if(uptime > config.load.subscribingClients.intermittentUptimeSeconds) {
                            log.info("Start downtime for intermittent client $clientId")
                            disconnectTimestamp = System.currentTimeMillis()
                            keepMqttClientDisconnected = true
                            mqttClient.disconnect()
                        }
                    } else {
                        val downtime = System.currentTimeMillis() - disconnectTimestamp
                        if(downtime > config.load.subscribingClients.intermittentDowntimeSeconds) {
                            log.info("Stop downtime for intermittent client $clientId")
                            keepMqttClientDisconnected = false
                            //let AbstractClient's connection check loop do the rest
                        }
                    }
                    delay(config.load.simulationStepInterval)
                }
            }
        }
    }

    override fun onClientConnect() {
        super.onClientConnect()
        connectTimestamp = System.currentTimeMillis()
    }

    companion object {
        private val log = LoggerFactory.getLogger(SubscribingClient::class.java)
    }
}