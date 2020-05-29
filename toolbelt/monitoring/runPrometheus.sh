#!/bin/bash
SCRIPT_DIR=`realpath $(dirname "$0")`

PROMETHEUS_PORT=9090

#interactive
#INTERACTIVE=-it
#non-interactive
INTERACTIVITY=-d

#No cleanup
#CLEANUP=""
#Remove container automatically when completed
CLEANUP="--rm"

CONTAINER_NAME=prometheus

docker run $INTERACTIVITY $CLEANUP \
    -v $SCRIPT_DIR/prometheusConfig.yml:/etc/prometheus/prometheus.yml \
     --network host \
     --name $CONTAINER_NAME \
     prom/prometheus:v2.12.0 \
     --config.file=/etc/prometheus/prometheus.yml \
     --web.listen-address 0.0.0.0:$PROMETHEUS_PORT
