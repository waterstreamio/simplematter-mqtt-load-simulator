package io.simplematter.mqtt.load.config

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import java.lang.IllegalArgumentException


data class MqttLoadSimulatorConfig(val load: LoadConfig,
                                   val mqtt: MqttConfig,
                                   val monitoring: MonitoringConfig
) {
    companion object {
        fun load(): MqttLoadSimulatorConfig {
            val config = ConfigFactory.load()
            return config.extract<MqttLoadSimulatorConfig>()
        }
    }
}

data class LoadConfig(
        val publishQos: Int,
        val subscribeQos: Int,
        val clientsMinNumber: Int,
        val clientsMaxNumber: Int,
        val clientsMaxSpawnAtOnce: Int,
        val clientPrefix: String,
        val randomizeClientPrefix: Boolean,
        val topicsNumber: Int,
        val topicPrefix: String,
        val messageMinSize: Int,
        val messageMaxSize: Int,
        val simulationStepInterval: Long,
        val clientActionProbabilities: ClientActionProbabilitiesConfig,
        val clientStepInterval: Long,
        val statsInterval: Long,
        val rampUpSeconds: Int,
        val actionsDuringRampUp: Boolean,
        val persistentSession: Boolean
) {
    val rampUpMillis = rampUpSeconds * 1000L
}

data class ClientActionProbabilitiesConfig(
        val publish: Int,
        val subscribe: Int,
        val unsubscribe: Int,
        val idle: Int) {
    val publishRange = publish
    val subscribeRange = publishRange + subscribe
    val unsubscribeRange = subscribeRange + unsubscribe

    val sum = publish + subscribe + unsubscribe + idle
}

data class MqttConfig(val server: String,
                      val connectionTimeoutSeconds: Int,
                      val keepAliveSeconds: Int) {
    val serverParsed: MqttServer by lazy {
        val parts = server.split(':', limit = 2)
        if (parts.size == 2)
            MqttServer(parts[0], parts[1].toInt())
        else
            throw IllegalArgumentException("Unable to parse server connection string: $server")
    }
}


data class MqttServer(val host: String, val port: Int)

data class MonitoringConfig(val port: Int? = 1884,
                            val metricsEndpoint: String = "/metrics",
                            val includeJavaMetrics: Boolean = true)

