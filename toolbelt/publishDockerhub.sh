#!/bin/sh
set -e
SCRIPT_DIR=`realpath $(dirname "$0")`
PROJECT_DIR=$SCRIPT_DIR/..

$SCRIPT_DIR/buildDocker.sh

. $PROJECT_DIR/gradle.properties
MQTT_LOAD_VERSION=$version

LOCAL_MQTT_LOAD_IMAGE=simplematter-mqtt-load-simulator
DH_MQTT_LOAD_IMAGE=simplematter/simplematter-mqtt-load-simulator

echo Pushing to $DH_MQTT_LOAD_IMAGE tags "$MQTT_LOAD_VERSION" and "latest"

docker tag $LOCAL_MQTT_LOAD_IMAGE:$MQTT_LOAD_VERSION $DH_MQTT_LOAD_IMAGE:$MQTT_LOAD_VERSION
docker tag $LOCAL_MQTT_LOAD_IMAGE:latest $DH_MQTT_LOAD_IMAGE:latest
docker push $DH_MQTT_LOAD_IMAGE:$MQTT_LOAD_VERSION
docker push $DH_MQTT_LOAD_IMAGE:latest

echo Push to $DH_MQTT_LOAD_IMAGE completed
date


