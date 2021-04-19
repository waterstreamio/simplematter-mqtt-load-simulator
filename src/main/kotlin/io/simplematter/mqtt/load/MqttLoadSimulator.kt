package io.simplematter.mqtt.load

import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.vertx.MetricsHandler
import io.simplematter.mqtt.load.clients.AbstractClient
import io.simplematter.mqtt.load.clients.PublishingClient
import io.simplematter.mqtt.load.clients.RandomizedClient
import io.simplematter.mqtt.load.clients.SubscribingClient
import io.simplematter.mqtt.load.config.MonitoringConfig
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random


class MqttLoadSimulator(private val vertx: Vertx, private val config: MqttLoadSimulatorConfig) : CoroutineScope {
    private val log = LoggerFactory.getLogger(MqttLoadSimulator::class.java)

    private val random = Random(System.currentTimeMillis())

    private val job by lazy {
        val j = SupervisorJob()
        j
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    private val topicGroupPatterns: List<String> = (1..config.load.topicGroupsNumber).map { g -> config.load.topicPrefix + "/" + g + "/#"}

    private val topics: List<String> = (1..config.load.topicsNumber).map { t ->
        val g = t % config.load.topicGroupsNumber
        config.load.topicPrefix + "/" + g + "/" + t }

    private var randomizedClients = listOf<RandomizedClient>()
    private var publishingClients = listOf<PublishingClient>()
    private var subscribingClients = listOf<SubscribingClient>()

    private val clientIndex = AtomicInteger(0)

    private val simulationStats = SimulationStats(job, config)

    private val rampUpCompletePromise = Promise.promise<Void>()

    private val clientPrefix = if (config.load.randomizeClientPrefix)
        config.load.clientPrefix + random.nextInt(10000) + "-"
    else
        config.load.clientPrefix

    private val randomizedClientPrefix = clientPrefix + "rnd-"

    private val publishingClientPrefix = clientPrefix + "pub-"

    private val subscribingClientPrefix = clientPrefix + "sub-"

    suspend fun run() {
        val startTimestamp = System.currentTimeMillis()

        while (true) {
            val nextAction = nextClientsAction()
            val now = System.currentTimeMillis()

            trimStoppedClients()

            val rampUpComplete = rampUpCompletePromise.future().isComplete

            if(!rampUpComplete) {
                val msSinceStart = now - startTimestamp
                randomizedClients = randomizedClients +
                    rampUp(msSinceStart, config.load.randomizedClients.clientsMinNumber, randomizedClients.size, "rnd", ::launchRandomizedClient )
                publishingClients = publishingClients +
                    rampUp(msSinceStart, config.load.publishingClients.clientsNumber, publishingClients.size, "pub", ::launchPublishingClient )
                subscribingClients = subscribingClients +
                    rampUp(msSinceStart, config.load.subscribingClients.clientsNumber, subscribingClients.size, "sub", ::launchSubscribingClient)
            }
            else if (randomizedClients.size < config.load.randomizedClients.clientsMinNumber ||
                    (nextAction == ClientsAction.INCREASE && randomizedClients.size < config.load.randomizedClients.clientsMaxNumber))
                spawnMoreRandomizedClients()
            else if (randomizedClients.size > config.load.randomizedClients.clientsMaxNumber ||
                    nextAction == ClientsAction.DECREASE && randomizedClients.size > config.load.randomizedClients.clientsMinNumber)
                closeSomeRandomizedClients()

            if(!rampUpComplete && randomizedClients.size >= config.load.randomizedClients.clientsMinNumber &&
                        publishingClients.size >= config.load.publishingClients.clientsNumber &&
                        subscribingClients.size >= config.load.subscribingClients.clientsNumber ) {
                rampUpCompletePromise.tryComplete()
            }

            delay(config.load.simulationStepInterval)
        }
    }

    private fun trimStoppedClients() {
        var trimCount = 0
        randomizedClients = randomizedClients.filter {
            if (it.isStopped()) {
                trimCount += 1
                false
            } else {
                true
            }
        }
        log.debug("Trimmed $trimCount clients")
    }

    private fun closeSomeRandomizedClients() {
        val nClients = random.nextInt(randomizedClients.size - config.load.randomizedClients.clientsMinNumber) + 1
        log.debug("Closing $nClients clients")
        randomizedClients = randomizedClients.filterIndexed { i, client ->
            if (i < nClients) {
                client.stop()
                simulationStats.clientsStopped(1)
                false
            } else if (client.isStopped()) {
                simulationStats.clientsStopped(1)
                false
            } else {
                true
            }
        }
    }

    private fun spawnMoreRandomizedClients() {
        val missingClients = Math.max(config.load.randomizedClients.clientsMinNumber - randomizedClients.size, 0)
        val clientsCapacity = Math.min(config.load.clientsMaxSpawnAtOnce, Math.max(config.load.randomizedClients.clientsMaxNumber - randomizedClients.size, 0))
        val nClients = if (missingClients >= clientsCapacity)
            clientsCapacity
        else
            random.nextInt(missingClients, clientsCapacity) + 1
        log.debug("Spawning $nClients clients")
        randomizedClients = randomizedClients + (0..nClients).map { launchRandomizedClient() }
    }

    private fun <C : AbstractClient> rampUp(
        msSinceStart: Long,
        targetClientsNumber: Int,
        actualClientsNumber: Int,
        clientType: String,
        spawner: () -> C
    ): List<C> {
        val expectedClients: Int = (msSinceStart * targetClientsNumber / config.load.rampUpMillis).toInt()
        val nRndClients = Math.max(expectedClients - actualClientsNumber, 0)
        log.debug("Expected ${clientType} clients: $expectedClients, actual: ${actualClientsNumber}. Ramping up $nRndClients clients.")
        return (0 until nRndClients).map { spawner() }
    }

    private fun launchRandomizedClient(): RandomizedClient {
        val clientId = randomizedClientPrefix + clientIndex.incrementAndGet()
        val client = RandomizedClient(clientId, topics, simulationStats, config, vertx, job, rampUpCompletePromise.future())
        client.start()
        simulationStats.clientsStarted(1)
        return client
    }

    private fun launchPublishingClient(): PublishingClient {
        val clientId = publishingClientPrefix + clientIndex.incrementAndGet()
        val client = PublishingClient(clientId, topics, simulationStats, config, vertx, job, rampUpCompletePromise.future())
        client.start()
        simulationStats.clientsStarted(1)
        return client
    }

    private fun launchSubscribingClient(): SubscribingClient {
        val clientId = subscribingClientPrefix + clientIndex.incrementAndGet()
        val client = SubscribingClient(clientId, topics, topicGroupPatterns, simulationStats, config, vertx, job, rampUpCompletePromise.future())
        client.start()
        simulationStats.clientsStarted(1)
        return client
    }

    private fun nextClientsAction(): ClientsAction {
        val n = random.nextInt(10)
        return if (n < 1)
            ClientsAction.DECREASE
        else if (n < 2)
            ClientsAction.INCREASE
        else
            ClientsAction.REMAIN
    }

    private enum class ClientsAction {
        DECREASE, INCREASE, REMAIN
    }

    companion object {
        private val log = LoggerFactory.getLogger(MqttLoadSimulator::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val vertx = Vertx.vertx();
            val config = MqttLoadSimulatorConfig.load()
            val simulator = MqttLoadSimulator(vertx, config)

            exposeMonitoringEndpoint(vertx, config.monitoring)

            runBlocking {
                simulator.run()
            }
        }

        fun exposeMonitoringEndpoint(vertx: Vertx, config: MonitoringConfig) {
            if (config.port != null) {
                if (config.includeJavaMetrics)
                    DefaultExports.initialize()
                val router = Router.router(vertx)
                router.route(config.metricsEndpoint).handler(MetricsHandler())
                val server = vertx.createHttpServer()
                server.requestHandler(router).listen(config.port)
                log.debug("Started monitoring on port ${config.port}")
            } else {
                log.info("Monitoring port not configured, not exposing metrics")
            }
        }
    }
}
