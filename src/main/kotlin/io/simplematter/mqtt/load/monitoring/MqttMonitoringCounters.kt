package io.simplematter.mqtt.load.monitoring

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary


object MqttMonitoringCounters {
    val clientsCurrent = Gauge.build("mqtt_load_clients_current", "Number of currently spawned clients (both connected and not)").register()

    val publishingClientsCurrent = Gauge.build("mqtt_load_publishing_clients_current", "Number of currently spawned publishing clients (both connected and not)").register()

    val subscribingClientsCurrent = Gauge.build("mqtt_load_subscribing_clients_current", "Number of currently spawned subscribing clients (both connected and not)").register()

    val randomizedClientsCurrent = Gauge.build("mqtt_load_randomized_clients_current", "Number of currently spawned randomized clients (both connected and not)").register()

    val clientsConnectedCurrent = Gauge.build("mqtt_load_clients_connected_current", "Number of currently connected clients").register()

    val publishSent = Counter.build("mqtt_load_publish_sent", "Number of PUBLISH messages sent to MQTT broker").register()

    val publishReceived = Counter.build("mqtt_load_publish_received", "Number of PUBLISH messages received from MQTT broker").register()

    val subscribeSent = Counter.build("mqtt_load_subscribe_sent", "Number of SUBSCRIBE messages sent to MQTT broker").register()

    val subackReceived = Counter.build("mqtt_load_suback_received", "Number of SUBACK messages received from MQTT broker").register()

    val subscribeLatency = Summary.build("mqtt_load_subscribe_latency", "Time spent waiting for SUBACK - summary")
        .quantile(0.99, 0.001)
        .quantile(0.90, 0.001)
        .quantile(0.50, 0.001)
        .register()


    val subscribeFailed = Counter.build("mqtt_load_subscribe_failed", "Number of failed subscriptions").register()

    val unsubscribeSent = Counter.build("mqtt_load_unsubscribe_sent", "Number of UNSUBSCRIBE messages sent to MQTT broker").register()

    val subscriptionsCurrent = Gauge.build("mqtt_load_subscriptions_current", "Number of current subscriptions, for both connected and disconnected clients").register()

    val subscriptionsDisconnectedCurrent = Gauge.build("mqtt_load_subscriptions_disconnected_current", "Number of current subscriptions in temporarily disconnected clients").register()

    val connectSent = Counter.build("mqtt_load_connect_sent", "Number of connect attempts").register()

    val connectSuccess = Counter.build("mqtt_load_connect_success", "Number of successful connects").register()

    val connectAborts = Counter.build("mqtt_load_connect_aborts", "Number of aborted connects").register()

    val connectFailures = Counter.build("mqtt_load_connect_failures", "Number of failed connects").register()

    val connectPending = Gauge.build("mqtt_load_connect_pending", "Number of pending connects").register()

    val disconnectsTotal = Counter.build("mqtt_load_disconnects_total", "Number of total disconnects").register()

    val disconnectsIntentional = Counter.build("mqtt_load_disconnects_intentional", "Number of intentional disconnects - initiated by load simulator").register()

    val connectSuccessDuration = Counter.build("mqtt_load_connect_success_duration", "Time spent waiting for successful connections").register()

    val connectSuccessLatency = Summary.build("mqtt_load_connect_success_latency", "Time spent waiting for successful connections - summary")
        .quantile(0.99, 0.001)
        .quantile(0.90, 0.001)
        .quantile(0.50, 0.001)
        .register()

    val connectFailDuration = Counter.build("mqtt_load_connect_fail_duration", "Time spent waiting for failed connections").register()

    val connectFailLatency = Summary.build("mqtt_load_connect_fail_latency", "Time spent waiting for failed connections - summary")
        .quantile(0.99, 0.001)
        .quantile(0.90, 0.001)
        .quantile(0.50, 0.001)
        .register()
}