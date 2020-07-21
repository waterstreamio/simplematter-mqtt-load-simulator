package io.simplematter.mqtt.load.monitoring

import io.prometheus.client.Counter
import io.prometheus.client.Gauge


object MqttMonitoringCounters {
    val clientsCurrent = Gauge.build("mqtt_load_clients_current", "Number of currently spawned clients (both connected and not)").register()

    val clientsConnectedCurrent = Gauge.build("mqtt_load_clients_connected_current", "Number of currently connected clients").register()

    val publishSent = Counter.build("mqtt_load_publish_sent", "Number of PUBLISH messages sent to MQTT broker").register()

    val publishReceived = Counter.build("mqtt_load_publish_received", "Number of PUBLISH messages received from MQTT broker").register()

    val subscribeSent = Counter.build("mqtt_load_subscribe_sent", "Number of SUBSCRIBE messages sent to MQTT broker").register()

    val unsubscribeSent = Counter.build("mqtt_load_unsubscribe_sent", "Number of UNSUBSCRIBE messages sent to MQTT broker").register()

    val subscriptionsCurrent = Gauge.build("mqtt_load_subscriptions_current", "Number of current subscriptions, for both connected and disconnected clients").register()

    val subscriptionsDisconnectedCurrent = Gauge.build("mqtt_load_subscriptions_disconnected_current", "Number of current subscriptions in temporarily disconnected clients").register()

    val connectSent = Counter.build("mqtt_load_connect_sent", "Number of connect attempts").register()

    val connectSuccess = Counter.build("mqtt_load_connect_success", "Number of successful connects").register()

    val connectFailures = Counter.build("mqtt_load_connect_failures", "Number of failed connects").register()

    val disconnectsTotal = Counter.build("mqtt_load_disconnects_total", "Number of total disconnects").register()

    val disconnectsIntentional = Counter.build("mqtt_load_disconnects_intentional", "Number of intentional disconnects - initiated by load simulator").register()
}