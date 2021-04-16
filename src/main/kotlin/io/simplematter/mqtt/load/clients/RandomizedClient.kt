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
) : CoroutineScope {

    private val log = LoggerFactory.getLogger(RandomizedClient::class.java)

    private val job by lazy {
        val j = Job(parentJob)

        j.invokeOnCompletion {
            stopping = true
            if (mqttClient != null && mqttClient.isConnected) mqttClient.disconnect()
            MqttMonitoringCounters.clientsCurrent.dec()
            MqttMonitoringCounters.subscriptionsCurrent.dec(subscriptions.size.toDouble())
            stopped = true
        }
        j
    }

    override val coroutineContext = Dispatchers.Default + job

    private var stopping = false

    private var stopped = false

    fun isStopped(): Boolean = stopped

    private val options = {
        val o = MqttClientOptions()
            .setClientId(clientId)
            .setCleanSession(!config.load.persistentSession)
            .setKeepAliveInterval(config.mqtt.keepAliveSeconds)
            .setAutoKeepAlive(config.mqtt.autoKeepAlive)

        o.setReconnectAttempts(0)
            .setConnectTimeout(config.mqtt.connectionTimeoutSeconds * 1000)

        o
    }()

    private val mqttClient = MqttClient.create(vertx, options)

    private val subscriptions = mutableSetOf<String>()

    private val publishQoS = MqttQoS.valueOf(config.load.publishQos)

    private val subacks = ConcurrentHashMap<Int, MqttSubAckMessage>()

    /**
     * Checks that mqttClient is connected. If not - tries to connect it
     */
    private fun startConnectionCheckLoop() {
        launch {
            var backOffInterval = config.load.randomizedClients.clientStepInterval
            while (!stopping) {
                try {
                    if (!mqttClient.isConnected) {
                        log.info("Client $clientId not connected, trying to connect it to ${config.mqtt.server}...")
                        MqttMonitoringCounters.connectPending.inc()
                        suspendCancellableCoroutine<Unit> { continuation ->
                            MqttMonitoringCounters.connectSent.inc()
                            val connectStartTimestamp = System.currentTimeMillis()
                            launch {
                                delay(config.mqtt.connectionTimeoutSeconds * 1000L * 2)
                                if(continuation.isActive) {
                                    MqttMonitoringCounters.connectAborts.inc()
                                    continuation.cancel(TimeoutException("MQTT client $clientId didn't complete connection for ${2 * config.mqtt.connectionTimeoutSeconds} s"))
                                    log.info("Client $clientId connection to ${config.mqtt.server} unresponsive, aborting it")
                                    try {
                                        mqttClient.disconnect()
                                    } catch (e: Exception) {
                                        log.info("Exception while aborting the connection", e)
                                    }
                                }
                            }
                            mqttClient.connect(config.mqtt.serverParsed.port, config.mqtt.serverParsed.host, { result ->
                                val now = System.currentTimeMillis()
                                val latency = (now - connectStartTimestamp).toDouble()
                                if (result.failed()) {
                                    backOffInterval = backOffInterval * 2
                                    log.info("Client $clientId failed to connect to ${config.mqtt.server}, increasing backOffInterval to ${backOffInterval}")
                                    continuation.resume(Unit)
                                    MqttMonitoringCounters.connectFailures.inc()
                                    MqttMonitoringCounters.connectFailLatency.observe(latency)
                                    MqttMonitoringCounters.connectFailDuration.inc(latency)
                                } else {
                                    backOffInterval = config.load.randomizedClients.clientStepInterval
                                    log.debug("Client $clientId connected to ${config.mqtt.server}")
                                    continuation.resume(Unit)
                                    MqttMonitoringCounters.connectSuccess.inc()
                                    MqttMonitoringCounters.clientsConnectedCurrent.inc()
                                    MqttMonitoringCounters.subscriptionsDisconnectedCurrent.dec(subscriptions.size.toDouble())
                                    MqttMonitoringCounters.connectSuccessLatency.observe(latency)
                                    MqttMonitoringCounters.connectSuccessDuration.inc(latency)
                                }
                            })
                        }
                        MqttMonitoringCounters.connectPending.dec()
                    }
                } catch (e: Exception) {
                    log.error("Unhandled exception in client $clientId connection check loop", e)
                }
                delay(backOffInterval)
            }
        }
    }

    private fun startActionLoop() {
        if(!config.mqtt.autoKeepAlive) {
            //automatic ping doesn't react on missing incoming messages
            launch {
                while (!stopping) {
                    if (mqttClient.isConnected) {
                        mqttClient.ping()
                    }
                    delay(config.mqtt.keepAliveSeconds * 1000L)
                }
            }
        }
        launch {
            while (!stopping) {
                val action = nextAction()

                if (!mqttClient.isConnected || !rampUpComplete.isComplete && !config.load.actionsDuringRampUp) {
                    //noop
                } else if (action == ClientAction.PUBLISH && topics.isNotEmpty()) {
                    sendRandomMessage()
                } else if (action == ClientAction.SUBSCRIBE && subscriptions.size < topics.size) {
                    subscribeRandomTopic()
                } else if (action == ClientAction.UNSUBSCRIBE && subscriptions.size > 0) {
                    unsubscribeRandomTopic()
                }
                delay(config.load.randomizedClients.clientStepInterval)
            }
        }
    }

    fun start() {
        MqttMonitoringCounters.clientsCurrent.inc()

        startConnectionCheckLoop()

        mqttClient.publishHandler {
            stats.messageRecieved()
            MqttMonitoringCounters.publishReceived.inc()
        }.subscribeCompletionHandler { suback ->
            subacks.put(suback.messageId(), suback)
            MqttMonitoringCounters.subackReceived.inc()
        }.closeHandler {
            log.info("Client $clientId connection closed")
            MqttMonitoringCounters.clientsConnectedCurrent.dec()
            MqttMonitoringCounters.disconnectsTotal.inc()
            MqttMonitoringCounters.subscriptionsDisconnectedCurrent.inc(subscriptions.size.toDouble())
        }

        startActionLoop()
    }

    private fun sendRandomMessage() {
        val topic = topics.get(random.nextInt(topics.size))

        mqttClient.publish(topic, Buffer.buffer(randomMessageBody()), publishQoS, false, false)
        log.debug("Sent random message from {} to {}", clientId, topic)
        MqttMonitoringCounters.publishSent.inc()
        stats.messageSent()
    }

    private suspend fun subscribeRandomTopic() {
        if(subscriptions.size < config.load.randomizedClients.maxSubscriptionsPerClient) {
            do {
                val topic = topics.get(random.nextInt(topics.size))
                val subscribed = if (!subscriptions.contains(topic)) {
                    try {
                        val subscribeTimestamp = System.currentTimeMillis()
                        val packetId = mqttClient.subscribeAwait(topic, MqttQoS.AT_MOST_ONCE.value())
                        MqttMonitoringCounters.subscribeSent.inc()

                        withTimeout(config.mqtt.connectionTimeoutSeconds * 10 * 1000L) {
                            do {
                                val suback = subacks.remove(packetId)
                                if(suback != null) {
                                    if (suback.grantedQoSLevels().size != 1) {
                                        throw IllegalArgumentException("Unexpected SUBACK items count: ${suback.grantedQoSLevels().size}")
                                    }
                                    if (suback.grantedQoSLevels().get(0) > 2) {
                                        throw IllegalArgumentException( "SUBACK with failure code: ${suback.grantedQoSLevels().get(0)}")
                                    }
                                } else {
                                    delay(10)
                                }
                            } while(suback == null)
                        }

                        val subscribeDuration = System.currentTimeMillis() - subscribeTimestamp
                        MqttMonitoringCounters.subscribeLatency.observe(subscribeDuration.toDouble())
                        if(subscribeDuration > 10000) {
                            log.info("Subscribe ${clientId} to ${topic} took ${subscribeDuration} ms")
                        }

                        subscriptions.add(topic)
                        log.debug("Sent SUBSCRIBE {} to {}", clientId, topic)
                        stats.subscribedToTopic()
                        MqttMonitoringCounters.subscriptionsCurrent.inc()
                    } catch (e: Exception) {
                        log.error("Subscribe ${clientId} to ${topic} failed", e)
                        MqttMonitoringCounters.subscribeFailed.inc()
                    }
                    true
                } else {
                    false
                }
            } while (!subscribed && subscriptions.size < topics.size)
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

    fun stop() {
        stopping = true
        MqttMonitoringCounters.disconnectsIntentional.inc()
        job.cancel()
    }

    private enum class ClientAction {
        PUBLISH, SUBSCRIBE, UNSUBSCRIBE, IDLE
    }

    private fun randomMessageBody(): String {
        val b = StringBuilder()
        val desiredLength = random.nextInt(config.load.messageMinSize, config.load.messageMaxSize)

        do {
            b.append(randomWord())
        } while (b.length < desiredLength)

        return b.substring(0, Math.min(b.length, config.load.messageMaxSize))
    }

    companion object {
        private val random = Random(System.currentTimeMillis())

        private fun randomWord(): String = messageParts.get(random.nextInt(messageParts.size))

        private val messageParts = listOf("hello", "how", "bye", "thanks", "sensor", "data", "temperature", "degrees", "initialized", "ok", "error")
    }
}