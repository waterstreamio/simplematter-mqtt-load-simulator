package io.simplematter.mqtt.load

import io.kotlintest.specs.StringSpec
import io.simplematter.mqtt.load.config.MqttLoadSimulatorConfig


class ConfigSpec: StringSpec() {
    init {
        "Load the config" {
            val config = MqttLoadSimulatorConfig.load()
        }
    }
}