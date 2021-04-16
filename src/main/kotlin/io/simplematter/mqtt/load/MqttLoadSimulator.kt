package io.simplematter.mqtt.load

import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.vertx.MetricsHandler
import io.simplematter.mqtt.load.clients.RandomizedClient
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

    private val topics: List<String> = (1..config.load.topicsNumber).map { i -> config.load.topicPrefix + i }
    private var clients = listOf<RandomizedClient>()

    private val clientIndex = AtomicInteger(0)

    private val simulationStats = SimulationStats(job, config)

    private val rampUpCompletePromise = Promise.promise<Void>()

    private val clientPrefix = if (config.load.randomizeClientPrefix)
        config.load.clientPrefix + random.nextInt(10000) + "-"
    else
        config.load.clientPrefix

    suspend fun run() {
        val startTimestamp = System.currentTimeMillis()
        val rampUpEnd = startTimestamp + config.load.rampUpMillis

        while (true) {
            val nextAction = nextClientsAction()
            val now = System.currentTimeMillis()

            trimClients()

            if (now < rampUpEnd)
                rampUp(now - startTimestamp)
            else if (clients.size < config.load.randomizedClients.clientsMinNumber ||
                    (nextAction == ClientsAction.INCREASE && clients.size < config.load.randomizedClients.clientsMaxNumber))
                spawnMoreClients()
            else if (clients.size > config.load.randomizedClients.clientsMaxNumber ||
                    nextAction == ClientsAction.DECREASE && clients.size > config.load.randomizedClients.clientsMinNumber)
                closeSomeClients()
            //TODO eliminate closed clients from the list

            if (now > rampUpEnd)
                rampUpCompletePromise.tryComplete()

            delay(config.load.simulationStepInterval)
        }
    }

    private fun trimClients() {
        var trimCount = 0
        clients = clients.filter {
            if (it.isStopped()) {
                trimCount += 1
                false
            } else {
                true
            }
        }
        log.debug("Trimmed $trimCount clients")
    }

    private fun closeSomeClients() {
        val nClients = random.nextInt(clients.size - config.load.randomizedClients.clientsMinNumber) + 1
        log.debug("Closing $nClients clients")
        clients = clients.filterIndexed { i, client ->
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

    private fun spawnMoreClients() {
        val missingClients = Math.max(config.load.randomizedClients.clientsMinNumber - clients.size, 0)
        val clientsCapacity = Math.min(config.load.clientsMaxSpawnAtOnce, Math.max(config.load.randomizedClients.clientsMaxNumber - clients.size, 0))
        val nClients = if (missingClients >= clientsCapacity)
            clientsCapacity
        else
            random.nextInt(missingClients, clientsCapacity) + 1
        log.debug("Spawning $nClients clients")
        clients = clients + (0..nClients).map { launchClient() }
    }

    private fun rampUp(msSinceStart: Long) {
        val expectedClients: Int = (msSinceStart * config.load.randomizedClients.clientsMinNumber / config.load.rampUpMillis).toInt()
        val nClients = Math.max(expectedClients - clients.size, 0)
        log.debug("Expected clients: $expectedClients, actual: ${clients.size}. Ramping up $nClients clients.")
        clients = clients + (0..nClients).map { launchClient() }
    }

    private fun launchClient(): RandomizedClient {
        val clientId = clientPrefix + clientIndex.incrementAndGet()
        val client = RandomizedClient(clientId, topics, simulationStats, config, vertx, job, rampUpCompletePromise.future())
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
