FROM openjdk:16-oraclelinux8

ARG MQTT_LOAD_VERSION
ENV MQTT_LOAD_VERSION=$MQTT_LOAD_VERSION

RUN mkdir -p /opt/simplematter-mqtt-load-simulator
WORKDIR /opt/simplematter-mqtt-load-simulator
COPY build/libs/simplematter-mqtt-load-simulator-${MQTT_LOAD_VERSION}-all.jar ./
COPY src/docker/resources/*.sh ./

RUN chmod +x *.sh

CMD ["/opt/simplematter-mqtt-load-simulator/simplematter-mqtt-load-simulator.sh"]

