package io.simplematter.mqtt.load

import io.simplematter.mqtt.load.clients.RandomizedClient
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger


class SimulationStats(parentJob: Job, config: MqttLoadSimulatorConfig): CoroutineScope {
    private val log = LoggerFactory.getLogger(RandomizedClient::class.java)

    private val job by lazy {
        val j = Job(parentJob)

        j
    }

    private val clientsCount = AtomicInteger(0)
    private val messagesSent = AtomicInteger(0)
    private val messagesReceived = AtomicInteger(0)
    private val topicsListening = AtomicInteger(0)

    override val coroutineContext = Dispatchers.Default + job

    init {
        launch {
            while(true) {
                log.info("Clients: ${clientsCount.get()}, topics listening: ${topicsListening.get()}, messages sent: ${messagesSent.get()}, messages received: ${messagesReceived.get()}")
                delay(config.load.statsInterval)
            }
        }
    }

    fun stop() {
        job.cancel()
    }

    fun clientsStarted(num: Int) {
        clientsCount.addAndGet(num)
    }

    fun clientsStopped(num: Int) {
        clientsCount.addAndGet(-num)
    }

    fun messageSent() {
        messagesSent.incrementAndGet()
    }

    fun messageRecieved() {
        messagesReceived.incrementAndGet()
    }

    fun subscribedToTopic() {
        topicsListening.incrementAndGet()
    }

    fun unsubscribedFromTopic() {
        topicsListening.decrementAndGet()
    }
}