#!/bin/sh

LIB=/opt/simplematter-mqtt-load-simulator
PROJECT_JAR=$LIB/simplematter-mqtt-load-simulator-0.0.1-SNAPSHOT-all.jar
CLASSPATH=$PROJECT_JAR

java -cp $CLASSPATH io.simplematter.mqtt.load.MqttLoadSimulator
