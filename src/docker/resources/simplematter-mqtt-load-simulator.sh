#!/bin/sh

LIB=/opt/simplematter-mqtt-load-simulator
PROJECT_JAR=$LIB/simplematter-mqtt-load-simulator-${MQTT_LOAD_VERSION}-all.jar
CLASSPATH=$PROJECT_JAR

java -cp $CLASSPATH io.simplematter.mqtt.load.MqttLoadSimulator
