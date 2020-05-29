#!/bin/sh

set -e
SCRIPT_DIR=`realpath $(dirname "$0")`
PROJECT_DIR=$SCRIPT_DIR/..

. $SCRIPT_DIR/config.sh

kubectl delete pods,services,configmaps,deployments,secret -l project=mqtt-load-simulator -n mqttload

kubectl delete servicemonitors -l project=mqtt-load-simulator -n mqttload || true
kubectl delete servicemonitors -l project=mqtt-load-simulator -n default || true
kubectl delete servicemonitors -l project=mqtt-load-simulator -n monitoring || true

kubectl delete configmaps mqtt-load-simulator-dashboard -n monitoring || true
