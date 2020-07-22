#!/bin/sh
set -e
SCRIPT_DIR=`realpath $(dirname "$0")`
PROJECT_DIR=$SCRIPT_DIR/..

cd $PROJECT_DIR
./gradlew shadowJar

. $PROJECT_DIR/gradle.properties
MQTT_LOAD_VERSION=$version

LOCAL_MQTT_LOAD_IMAGE=simplematter-mqtt-load-simulator

docker build --build-arg MQTT_LOAD_VERSION=${MQTT_LOAD_VERSION} -t $LOCAL_MQTT_LOAD_IMAGE:$MQTT_LOAD_VERSION -f $PROJECT_DIR/Dockerfile $PROJECT_DIR
docker tag $LOCAL_MQTT_LOAD_IMAGE:$MQTT_LOAD_VERSION $LOCAL_MQTT_LOAD_IMAGE:latest
