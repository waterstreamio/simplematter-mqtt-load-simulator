#!/bin/bash
SCRIPT_DIR=`realpath $(dirname "$0")`

GRAFANA_PORT=3000

#interactive
#INTERACTIVE=-it
#non-interactive
INTERACTIVITY=-d

#No cleanup
#CLEANUP=""
#Remove container automatically when completed
CLEANUP="--rm"

CONTAINER_NAME=grafana

docker run $INTERACTIVITY $CLEANUP \
     --name $CONTAINER_NAME \
     --network host \
     grafana/grafana:6.3.6

#     -p $GRAFANA_PORT:3000 \
