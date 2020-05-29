#!/bin/sh

set -e
SCRIPT_DIR=`realpath $(dirname "$0")`
PROJECT_DIR=$SCRIPT_DIR/..

. $SCRIPT_DIR/config.sh

kubectl apply -f ${SCRIPT_DIR}/mqtt_load_namespace.yaml

kubectl create secret docker-registry dockerhub-mqttd-readonly -n mqttload --docker-server=index.docker.io --docker-username=$DOCKERHUB_USERNAME --docker-password=$DOCKERHUB_PASSWORD
kubectl label secret dockerhub-mqttd-readonly project=mqtt-load-simulator -n mqttload


cat << EOF | eval kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mqtt-load-simulator
  namespace: mqttload
  labels:
    project: mqtt-load-simulator
    app: mqtt-load-simulator
spec:
  selector:
    matchLabels:
      #must match template.metadata.labels so that k8s could correctly identify pods of the deployment
      app: mqtt-load-simulator
  replicas: $MQTT_LOAD_REPLICAS
  strategy:
    type: RollingUpdate
  template:
    metadata:
      labels:
        project: mqtt-load-simulator
        app: mqtt-load-simulator
    spec:
      affinity:
#        nodeAffinity:
#          requiredDuringSchedulingIgnoredDuringExecution:
#            nodeSelectorTerms:
#              - matchExpressions:
#                  - key: "cloud.google.com/gke-nodepool"
#                    operator: In
#                    values:
#                      - aux-car-demo-node-pool
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - mqtt-load-simulator
              topologyKey: "kubernetes.io/hostname"
      imagePullSecrets:
        - name: dockerhub-mqttd-readonly
      containers:
        - name: mqtt-load-simulator
          image: simplematter/simplematter-mqtt-load-simulator:0.0.2-SNAPSHOT
          imagePullPolicy: Always
          resources:
            requests:
              cpu: $MQTT_LOAD_CPU_CORES
              memory: $MQTT_LOAD_RAM
          ports:
            - name: http-metrics
              containerPort: 1884
          env:
            - name: MQTT_LOAD_SERVER
              value: "$MQTT_LOAD_SERVER"
            - name: MQTT_LOAD_MIN_CLIENTS
              value: "$MQTT_LOAD_MIN_CLIENTS"
            - name: MQTT_LOAD_MAX_CLIENTS
              value: "$MQTT_LOAD_MAX_CLIENTS"
            - name: MQTT_LOAD_CLIENT_PREFIX
              value: "$MQTT_LOAD_CLIENT_PREFIX"
            - name: MQTT_LOAD_TOPIC_PREFIX
              value: "$MQTT_LOAD_TOPIC_PREFIX"
            - name: MQTT_LOAD_TOPICS_NUMBER
              value: "$MQTT_LOAD_TOPICS_NUMBER"
            - name: MQTT_LOAD_MESSAGE_MIN_SIZE
              value: "$MQTT_LOAD_MESSAGE_MIN_SIZE"
            - name: MQTT_LOAD_MESSAGE_MAX_SIZE
              value: "$MQTT_LOAD_MESSAGE_MAX_SIZE"
            - name: MQTT_LOAD_CLIENT_PUBLISH_PROBABILITY
              value: "$MQTT_LOAD_CLIENT_PUBLISH_PROBABILITY"
            - name: MQTT_LOAD_CLIENT_SUBSCRIBE_PROBABILITY
              value: "$MQTT_LOAD_CLIENT_SUBSCRIBE_PROBABILITY"
            - name: MQTT_LOAD_CLIENT_UNSUBSCRIBE_PROBABILITY
              value: "$MQTT_LOAD_CLIENT_UNSUBSCRIBE_PROBABILITY"
            - name: MQTT_LOAD_CLIENT_IDLE_PROBABILITY
              value: "$MQTT_LOAD_CLIENT_IDLE_PROBABILITY"
            - name: MQTT_LOAD_SIMULATION_STEP_INTERVAL
              value: "$MQTT_LOAD_SIMULATION_STEP_INTERVAL"
            - name: MQTT_LOAD_CLIENT_STEP_INTERVAL
              value: "$MQTT_LOAD_CLIENT_STEP_INTERVAL"
            - name: MQTT_LOAD_STATS_INTERVAL
              value: "$MQTT_LOAD_STATS_INTERVAL"
            - name: MQTT_LOAD_RAMP_UP_SECONDS
              value: "$MQTT_LOAD_RAMP_UP_SECONDS"
            - name: MQTT_LOAD_ACTIONS_DURING_RAMP_UP
              value: "$MQTT_LOAD_ACTIONS_DURING_RAMP_UP"
            - name: MQTT_LOAD_CONNECTION_TIMEOUT_SECONDS
              value: "$MQTT_LOAD_CONNECTION_TIMEOUT_SECONDS"
            - name: MQTT_LOAD_KEEP_ALIVE_SECONDS
              value: "$MQTT_LOAD_KEEP_ALIVE_SECONDS"
            - name: MQTT_LOAD_MONITORING_PORT
              value: "$MQTT_LOAD_MONITORING_PORT"
EOF


kubectl apply -f ${SCRIPT_DIR}/mqtt_load_monitoring_service.yaml
kubectl apply -f ${SCRIPT_DIR}/mqtt_load_service_monitor.yaml

echo "Creating the mqtt-load-simulator monitoring dashboard configuration"
kubectl create -n monitoring configmap mqtt-load-simulator-dashboard --from-file=${SCRIPT_DIR}/../monitoring/mqtt-load-simulator-dashboard.json || true
kubectl label -n monitoring configmap/mqtt-load-simulator-dashboard grafana_dashboard=1 || true

