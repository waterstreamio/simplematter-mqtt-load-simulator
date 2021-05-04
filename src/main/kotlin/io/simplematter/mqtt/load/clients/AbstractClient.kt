package io.simplematter.mqtt.load.clients

import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.simplematter.mqtt.load.monitoring.MqttMonitoringCounters
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume


abstract class AbstractClient(
    private val clientId: String,
    private val config: MqttLoadSimulatorConfig,
    private val vertx: Vertx,
    private val parentJob: Job,
    private val rampUpComplete: Future<Void>
): CoroutineScope {

    protected val job by lazy {
        val j = Job(parentJob)

        j.invokeOnCompletion {
            stopping = true
            try {
                if (mqttClient != null && mqttClient.isConnected) mqttClient.disconnect()
            } catch (e: Exception) {
                log.debug("Exception in client disconnect", e)
            }
            MqttMonitoringCounters.clientsCurrent.dec()
            stopped = true
        }
        j
    }

    override val coroutineContext = Dispatchers.Default + job

    private var stopping = false

    fun isStopping(): Boolean = stopping

    private var stopped = false

    fun isStopped(): Boolean = stopped

    @Volatile
    protected var keepMqttClientDisconnected: Boolean = false

    private val options = {
        val o = MqttClientOptions()
            .setClientId(clientId)
            .setCleanSession(!config.load.persistentSession)
            .setKeepAliveInterval(config.mqtt.keepAliveSeconds)
            .setAutoKeepAlive(config.mqtt.autoKeepAlive)
            .setMaxMessageSize(config.load.messageMaxSize + 100)

        o.setReconnectAttempts(0)
            .setConnectTimeout(config.mqtt.connectionTimeoutSeconds * 1000)

        o
    }()

    protected val mqttClient = MqttClient.create(vertx, options)

    /**
     * Checks that mqttClient is connected. If not - tries to connect it
     */
    private fun startConnectionCheckLoop() {
        launch {
            var backOffInterval = config.load.randomizedClients.clientStepInterval
            while (!stopping) {
                try {
                    if (!mqttClient.isConnected && !keepMqttClientDisconnected) {
                        log.info("Client $clientId not connected, trying to connect it to ${config.mqtt.server}...")
                        MqttMonitoringCounters.connectPending.inc()
                        suspendCancellableCoroutine<Unit> { continuation ->
                            MqttMonitoringCounters.connectSent.inc()
                            val connectStartTimestamp = System.currentTimeMillis()
                            launch {
                                delay(config.mqtt.connectionTimeoutSeconds * 1000L * 2)
                                if (continuation.isActive) {
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
                                val latency = now - connectStartTimestamp
                                if (result.failed()) {
                                    backOffInterval = backOffInterval * 2
                                    log.info("Client $clientId failed to connect to ${config.mqtt.server}, increasing backOffInterval to ${backOffInterval}")
                                    continuation.resume(Unit)
                                    MqttMonitoringCounters.connectFailures.inc()
                                    MqttMonitoringCounters.connectFailLatency.observe(latency.toDouble())
                                    MqttMonitoringCounters.connectFailDuration.inc(latency.toDouble())
                                } else {
                                    backOffInterval = config.load.randomizedClients.clientStepInterval
                                    log.debug("Client $clientId connected to ${config.mqtt.server}")
                                    continuation.resume(Unit)
                                    MqttMonitoringCounters.connectSuccess.inc()
                                    MqttMonitoringCounters.clientsConnectedCurrent.inc()
                                    MqttMonitoringCounters.connectSuccessLatency.observe(latency.toDouble())
                                    MqttMonitoringCounters.connectSuccessDuration.inc(latency.toDouble())
                                    onClientConnect(latency)
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

    protected open fun onClientConnect(latency: Long): Unit {
    }

    protected open fun onMqttClientClose(): Unit {
    }

    protected fun startPingLoopIfNeeded() {
        if (!config.mqtt.autoKeepAlive) {
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
    }

    /**
     * Extend in the subclass to specify client-specific behavior
     */
    open fun start() {
        MqttMonitoringCounters.clientsCurrent.inc()

        startConnectionCheckLoop()

        mqttClient.closeHandler {
            log.info("Client $clientId connection closed")
            MqttMonitoringCounters.clientsConnectedCurrent.dec()
            MqttMonitoringCounters.disconnectsTotal.inc()
            onMqttClientClose()
        }

        startPingLoopIfNeeded()
    }

    fun stop() {
        stopping = true
        MqttMonitoringCounters.disconnectsIntentional.inc()
        job.cancel()
    }

    fun canDoActions(): Boolean = mqttClient.isConnected && (rampUpComplete.isComplete || config.load.actionsDuringRampUp)

    companion object {
        private val log = LoggerFactory.getLogger(AbstractClient::class.java)
    }
}
