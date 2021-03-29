provider "google" {
  credentials = file("account.json")
  project = var.project
  region = var.region
}

# Use a random suffix to prevent overlap in network names
resource "random_string" "suffix" {
  length = 4
  special = false
  upper = false
}

resource "google_compute_network" "net" {
  name = "${var.simulation_name}-network-${random_string.suffix.result}"
}

resource "google_compute_subnetwork" "subnet" {
  name = "${var.simulation_name}-subnetwork-${random_string.suffix.result}"
  network = google_compute_network.net.self_link
  ip_cidr_range = var.vpc_cidr_block
  region = var.region
}

resource "google_compute_router" "router" {
  name = "${var.simulation_name}-router-${random_string.suffix.result}"
  region = google_compute_subnetwork.subnet.region
  network = google_compute_network.net.self_link

  bgp {
    asn = 64514
  }
}

resource "google_compute_router_nat" "nat" {
  name = "${var.simulation_name}-router-nat-${random_string.suffix.result}"
  router = google_compute_router.router.name
  region = google_compute_router.router.region
  nat_ip_allocate_option = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}

resource "google_compute_firewall" "mqtt-loadsim" {
  name = "${var.simulation_name}-firewall"
  network = google_compute_network.net.name

  allow {
    protocol = "tcp"
    ports = [
      "22",
      "1884",
      "3000",
      "9090"
    ]
  }

  source_ranges = [
    "0.0.0.0/0"]
}


resource "google_compute_instance_template" "mqtt-loadsim" {
  name = "mqtt-loadsim-template"
  description = "This template is used to create MQTT Load Simulator instances."

  tags = [
    "mqttloadsim"]

  depends_on = [
    google_compute_router_nat.nat]

  labels = {
    environment = "test"
  }

  instance_description = "MQTT Load Simulator instances"
  machine_type = var.node_type
  can_ip_forward = false

  scheduling {
    automatic_restart = true
    on_host_maintenance = "MIGRATE"
  }

  disk {
    source_image = "cos-cloud/cos-stable"
    auto_delete = true
    boot = true
  }

  network_interface {
//    network = "default"
    network = google_compute_network.net.id
    subnetwork = google_compute_subnetwork.subnet.id

    //Grant public IP - without it outbound connection count is very limited
    access_config { }
  }

  metadata = {
    app = "mqtt-loadsim"

    run-mqtt-loadsim-sh = <<EOF
docker run -d  \
    -e MQTT_LOAD_SERVER=${var.mqtt_server} \
    -e MQTT_LOAD_MIN_CLIENTS=${var.clients_per_node} \
    -e MQTT_LOAD_MAX_CLIENTS=${var.clients_per_node} \
    -e MQTT_LOAD_CLIENT_PREFIX=${var.simulation_name} \
    -e MQTT_LOAD_TOPIC_PREFIX=${var.simulation_name}/ \
    -e MQTT_LOAD_TOPICS_NUMBER=${var.clients_per_node} \
    -e MQTT_LOAD_MESSAGE_MIN_SIZE=${var.message_min_size} \
    -e MQTT_LOAD_MESSAGE_MAX_SIZE=${var.message_max_size} \
    -e MQTT_LOAD_CLIENT_PUBLISH_PROBABILITY=100 \
    -e MQTT_LOAD_CLIENT_SUBSCRIBE_PROBABILITY=0 \
    -e MQTT_LOAD_CLIENT_UNSUBSCRIBE_PROBABILITY=0 \
    -e MQTT_LOAD_CLIENT_IDLE_PROBABILITY=0 \
    -e MQTT_LOAD_SIMULATION_STEP_INTERVAL=${var.simulation_step_interval_ms} \
    -e MQTT_LOAD_CLIENT_STEP_INTERVAL=${var.client_step_interval_ms} \
    -e MQTT_LOAD_STATS_INTERVAL=10000 \
    -e MQTT_LOAD_RAMP_UP_SECONDS=${var.ramp_up_seconds} \
    -e MQTT_LOAD_ACTIONS_DURING_RAMP_UP=${var.actions_during_ramp_up} \
    -e MQTT_LOAD_CONNECTION_TIMEOUT_SECONDS=${var.connection_timeout_seconds} \
    -e MQTT_LOAD_KEEP_ALIVE_SECONDS=${var.keep_alive_seconds} \
    -e MQTT_LOAD_MONITORING_PORT=1884 \
    -e MQTT_LOAD_PERSISTENT_SESSION=${var.persistent_session} \
    -e MQTT_LOAD_PUBLISH_QOS=${var.message_qos} \
    -p 1884:1884 \
    --name mqtt-load-simulator \
    simplematter/simplematter-mqtt-load-simulator:${var.mqtt_loadsim_version}
EOF

    //TODO delay docker login until routing to the outer world is established
    user-data = <<EOF
#cloud-config

users:
  - name: mqttload
    groups: docker
runcmd:
  - mkdir /var/mqttload
  - cd /var/mqttload
  - echo simulation ${var.simulation_name} >> mqttload_start.log
  - echo scripts download start >> mqttload_start.log
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/run-mqtt-loadsim-sh", -H, "Metadata-Flavor: Google", -o, runMqttLoadsim.sh]
  - echo scripts download done >> mqttload_start.log
  - sleep 10
  - sudo -u mqttload sh runMqttLoadsim.sh
  - echo init done >>  mqttload_start.log
EOF
  }

  service_account {
    scopes = [
      "userinfo-email",
      "compute-ro",
      "storage-ro",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
      "https://www.googleapis.com/auth/devstorage.read_only"
    ]
  }
}

data "google_compute_instance_group" "mqtt_loadsim_nodes" {
  name = google_compute_instance_group_manager.mqtt_loadsim_group.name
  zone = var.zone
}

data "google_compute_instance" "mqtt_loadsim_nodes" {
  for_each = data.google_compute_instance_group.mqtt_loadsim_nodes.instances
  self_link = each.key
}

output "prometheus_url" {
  value = format("http://%s:9090", google_compute_instance_from_template.mqtt_load_monitoring.network_interface.0.access_config.0.nat_ip)
}

output "grafana_url" {
  value = format("http://%s:3000", google_compute_instance_from_template.mqtt_load_monitoring.network_interface.0.access_config.0.nat_ip)
}

resource "google_compute_target_pool" "mqtt_loadsim" {
  name = "${var.simulation_name}-instance-pool"

//  health_checks = [
//    google_compute_http_health_check.mqtt_loadsim_monitoring.name,
//  ]
}

resource "google_compute_instance_group_manager" "mqtt_loadsim_group" {
  name = "${var.simulation_name}-group-manager"

  base_instance_name = "mqtt-load"
  zone = var.zone

  depends_on = [
    google_compute_router_nat.nat]

  version {
    instance_template = google_compute_instance_template.mqtt-loadsim.self_link
  }

  target_pools = [
    google_compute_target_pool.mqtt_loadsim.self_link]
  target_size = var.node_count
  wait_for_instances = true

  named_port {
    name = "monitoring"
    port = 1884
  }

  //    auto_healing_policies {
  //      health_check = google_compute_http_health_check.mqttd_monitoring.self_link
  //      initial_delay_sec = 300
  //    }
}


resource "google_compute_instance_template" "mqtt-loadsim-monitoring" {
  name = "mqtt-load-monitoring-template"
  description = "This template is used to create MQTT Load Simulator monitoring instances."

  tags = [
    "mqttload",
    "monitoring"]

  depends_on = [
    google_compute_router_nat.nat
    //    local_file.prometheus_yaml
  ]

  labels = {
    environment = "test"
  }

  instance_description = "MQTT Load Simulator monitoring instance"
  machine_type = var.monitoring_node_type
  can_ip_forward = false

  scheduling {
    automatic_restart = true
    on_host_maintenance = "MIGRATE"
  }

  disk {
    source_image = "cos-cloud/cos-stable"
    auto_delete = true
    boot = true
  }

  network_interface {
//    network = "default"
//    network = google_compute_network.net.id
    subnetwork = google_compute_subnetwork.subnet.id

    //grant public IP
    access_config {}
  }

  metadata = {
    app = "mqtt-load-monitoring"
    mqtt-load-grafana-dashboard-json = file("../monitoring/mqtt-load-simulator-dashboard.json")
    prometheus-yaml = templatefile("prometheus.yaml.template", {
      targetsStr: join(",", [for x in data.google_compute_instance.mqtt_loadsim_nodes: format("'%s:%d'", x.network_interface.0.network_ip, 1884)])
    })

    grafana-prometheus-datasource-yaml = <<EOF
apiVersion: 1

datasources:
- name: Prometheus
  type: prometheus
  # <string, required> access mode. proxy or direct (Server or Browser in the UI). Required
  access: proxy
  url: http://prometheus:9090
  isDefault: true
  editable: false
EOF

    grafana-dashboard-provider-yaml = <<EOF
apiVersion: 1

providers:
- name: 'Dashboards'
  folder: ''
  type: file
  disableDeletion: true
  editable: true
  updateIntervalSeconds: 30
  # <bool> allow updating provisioned dashboards from the UI
  allowUiUpdates: true
  options:
    path: /var/mqtt_load_monitoring_dashboards
EOF

    run-grafana-sh = <<EOF
#TODO provision anonymous organization for Grafana
docker run -d -v /var/mqtt_load_monitoring/datasource.yaml:/etc/grafana/provisioning/datasources/datasource.yaml \
              -v /var/mqtt_load_monitoring/dashboard-provider.yaml:/etc/grafana/provisioning/dashboards/provider.yaml \
              -v /var/mqtt_load_monitoring/dashboards:/var/mqtt_load_monitoring_dashboards \
              --network monitoring --name grafana -p 3000:3000 \
              -e GF_SECURITY_ALLOW_EMBEDDING=true \
              -e GF_AUTH_ANONYMOUS_ENABLED=true \
              -e GF_AUTH_ANONYMOUS_ORG_NAME=anonymous_org \
              -e GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer grafana/grafana:6.3.6
EOF

    user-data = <<EOF
#cloud-config

users:
  - name: mqttload
    groups: docker
runcmd:
  - mkdir /var/mqtt_load_monitoring
  - cd /var/mqtt_load_monitoring
  - echo cluster ${var.simulation_name} >> start.log
  - echo scripts download start >> start.log
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/prometheus-yaml", -H, "Metadata-Flavor: Google", -o, prometheus.yaml]
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/run-grafana-sh", -H, "Metadata-Flavor: Google", -o, runGrafana.sh]
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/grafana-prometheus-datasource-yaml", -H, "Metadata-Flavor: Google", -o, datasource.yaml]
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/grafana-dashboard-provider-yaml", -H, "Metadata-Flavor: Google", -o, dashboard-provider.yaml]
  - mkdir dashboards
  - [curl, "http://metadata.google.internal/computeMetadata/v1/instance/attributes/mqtt-load-grafana-dashboard-json", -H, "Metadata-Flavor: Google", -o, dashboards/mqtt-load-grafana-dashboard.json]
  - echo starting prometheus >> start.log
  - docker network create monitoring
  - docker run -d -v /var/mqtt_load_monitoring/prometheus.yaml:/etc/prometheus/prometheus.yaml --network monitoring -p 9090:9090 --name prometheus prom/prometheus:v2.16.0 --config.file=/etc/prometheus/prometheus.yaml --web.listen-address 0.0.0.0:9090
  - echo starting grafana >> start.log
  - chmod a+x runGrafana.sh
  - sh runGrafana.sh
  - echo init done >>  start.log

EOF
  }

  service_account {
    scopes = [
      "userinfo-email",
      "compute-ro",
      "storage-ro",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
      "https://www.googleapis.com/auth/devstorage.read_only"
    ]
  }
}


resource "google_compute_instance_from_template" "mqtt_load_monitoring" {
  name = "mqtt-load-monitoring"
  zone = var.zone
  depends_on = [
    google_compute_instance_group_manager.mqtt_loadsim_group
  ]
  source_instance_template = google_compute_instance_template.mqtt-loadsim-monitoring.self_link
}

