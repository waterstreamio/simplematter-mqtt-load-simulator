FROM openjdk:8

RUN mkdir -p /opt/simplematter-mqtt-load-simulator
WORKDIR /opt/simplematter-mqtt-load-simulator
COPY build/libs/simplematter-mqtt-load-simulator-0.0.1-SNAPSHOT-all.jar ./
COPY src/docker/resources/*.sh ./

RUN chmod +x *.sh

CMD ["/opt/simplematter-mqtt-load-simulator/simplematter-mqtt-load-simulator.sh"]

