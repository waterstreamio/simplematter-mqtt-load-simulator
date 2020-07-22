package io.simplematter.mqtt.load

import io.netty.handler.codec.mqtt.MqttQoS
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random


class SimulatedClient(
        private val clientId: String,
        private val topics: List<String>,
        private val stats: SimulationStats,
        private val config: MqttLoadSimulatorConfig,
        private val vertx: Vertx,
        private val parentJob: Job,
        private val rampUpComplete: Future<Void>
) : CoroutineScope {

    private val log = LoggerFactory.getLogger(SimulatedClient::class.java)

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
        val o = MqttClientOptions().setClientId(clientId).setCleanSession(!config.load.persistentSession).setKeepAliveTimeSeconds(config.mqtt.keepAliveSeconds)
        o.setConnectTimeout(config.mqtt.connectionTimeoutSeconds * 1000)
        o
    }()

    private val mqttClient = MqttClient.create(vertx, options)

    private val subscriptions = mutableSetOf<String>()

    /**
     * Checks that mqttClient is connected. If not - tries to connect it
     */
    private fun startConnectionCheckLoop() {
        launch {
            var backOffInterval = config.load.clientStepInterval
            while (!stopping) {
                if(!mqttClient.isConnected) {
                    log.debug("Client $clientId not connected, trying to connect it to ${config.mqtt.server}...")
                    suspendCancellableCoroutine<Unit> { continuation ->
                        MqttMonitoringCounters.connectSent.inc()
                        mqttClient.connect(config.mqtt.serverParsed.port, config.mqtt.serverParsed.host, { result ->
                            if (result.failed()) {
                                backOffInterval = backOffInterval * 2
                                log.info("Client $clientId failed to connect to ${config.mqtt.server}, increasing backOffInterval to ${backOffInterval}")
                                continuation.resume(Unit)
                                MqttMonitoringCounters.connectFailures.inc()
                            } else {
                                backOffInterval = config.load.clientStepInterval
                                log.debug("Client $clientId connected to ${config.mqtt.server}")
                                continuation.resume(Unit)
                                MqttMonitoringCounters.connectSuccess.inc()
                                MqttMonitoringCounters.clientsConnectedCurrent.inc()
                                MqttMonitoringCounters.subscriptionsDisconnectedCurrent.dec(subscriptions.size.toDouble())
                            }
                        })
                    }
                }
                delay(backOffInterval)
            }
        }
    }

    private fun startActionLoop() {
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
                delay(config.load.clientStepInterval)
            }
        }
    }

    fun start() {
        MqttMonitoringCounters.clientsCurrent.inc()

        startConnectionCheckLoop()

        mqttClient.publishHandler {
            stats.messageRecieved()
            MqttMonitoringCounters.publishReceived.inc()
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
        mqttClient.publish(topic, Buffer.buffer(randomMessageBody()), MqttQoS.AT_MOST_ONCE, false, false)
        log.debug("Sent random message from {} to {}", clientId, topic)
        MqttMonitoringCounters.publishSent.inc()
        stats.messageSent()
    }

    private fun subscribeRandomTopic() {
        do {
            val topic = topics.get(random.nextInt(topics.size))
            val subscribed = if (!subscriptions.contains(topic)) {
                mqttClient.subscribe(topic, MqttQoS.AT_MOST_ONCE.value())
                subscriptions.add(topic)
                log.debug("Subscribed {} to {}", clientId, topic)
                stats.subscribedToTopic()
                MqttMonitoringCounters.subscriptionsCurrent.inc()
                MqttMonitoringCounters.subscribeSent.inc()
                true
            } else {
                false
            }
        } while (!subscribed && subscriptions.size < topics.size)
    }

    private fun unsubscribeRandomTopic() {
        val topic = subscriptions.random(random)
        mqttClient.unsubscribe(topic)
        log.debug("Unsubscribed {} from {}", clientId, topic)
        subscriptions.remove(topic)
        stats.unsubscribedFromTopic()
        MqttMonitoringCounters.subscriptionsCurrent.dec()
        MqttMonitoringCounters.unsubscribeSent.inc()
    }

    private fun nextAction(): ClientAction {
        val p = config.load.clientActionProbabilities

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