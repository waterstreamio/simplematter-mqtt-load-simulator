variable project {
  type = string
  description = "todo-add-your-gcp-project-name"
}

variable "region" {
}

variable "zone" {
}

variable "node_type" {
  default = "n1-standard-1"
}

variable "monitoring_node_type" {
  default = "n1-standard-1"
}

variable "preemptible_nodes" {
  default = "false"
}

variable "loadsim_ram_percentage" {
  description = "JVM MaxRAMPercentage parameter value"
  type        = string
  default     = "75.0"
}

variable "simulation_name" {
  default = "mqtt-load"
}

variable "master_ipv4_cidr_block" {
  description = "The IP range in CIDR notation (size must be /28) to use for the hosted master network. This range will be used for assigning internal IP addresses to the master or set of masters, as well as the ILB VIP. This range must not overlap with any other ranges in use within the cluster's network."
  type        = string
  default = "172.16.0.0/28"
}

# For the example, we recommend a /16 network for the VPC. Note that when changing the size of the network,
# you will have to adjust the 'cidr_subnetwork_width_delta' in the 'vpc_network' -module accordingly.
variable "vpc_cidr_block" {
  description = "The IP address range of the VPC in CIDR notation. A prefix of /16 is recommended. Do not use a prefix higher than /27."
  type        = string
  default     = "10.3.0.0/16"
}

###########################################
#############     MQTT Load   #############
###########################################

variable "mqtt_server" {

}

variable "mqtt_loadsim_version" {
  default = "0.0.6"
}

variable "node_count" {
  default = 3
  type = number
}

#Topic settings
variable "mqtt_topic_groups_number" {
  default = 10
  type = number
}

variable "mqtt_topics_number" {
  default = 1000
  type = number
}

#Message settings
variable "message_min_size" {
  default = 300
}

variable "message_max_size" {
  default = 600
}


#Publishing clients
variable "publishing_clients_per_node" {
  default = 1000
  type = number
}

variable "publishing_client_messages_per_second" {
  default = 0.1
  type = number
}

#Subscribing clients
variable "subscribing_clients_per_node" {
  default = 0
  type = number
}

variable "subscribing_client_wildcard_subscriptions" {
  default = 0
  type = number
}

variable "subscribing_client_regular_subscriptions" {
  default = 0
  type = number
}

variable "subscribing_client_delay_between_subscriptions" {
  default = 10
  type = number
}

variable "subscribing_client_intermittent_clients_per_node" {
  default = 0
  type = number
}

variable "subscribing_client_intermittent_clients_uptime_seconds" {
  default = 0
  type = number
}

variable "subscribing_client_intermittent_clients_downtime_seconds" {
  default = 0
  type = number
}

#Randomized clients
variable "randomized_clients_per_node" {
  default = 0
  type = number
}

variable "randomized_client_min_subscriptions_per_client" {
  default = 0
}

variable "randomized_client_max_subscriptions_per_client" {
  default = 50
}

variable "randomized_client_publish_probability" {
  default = 100
}

variable "randomized_client_subscribe_probability" {
  default = 0
}

variable "randomized_client_unsubscribe_probability" {
  default = 0
}

variable "randomized_client_idle_probability" {
  default = 0
}

variable "randomized_client_step_interval_ms" {
  default = 10000
}

#Further simulation settings
variable "ramp_up_seconds" {
  default = 120
}

variable "actions_during_ramp_up" {
  default = "false"
}

variable "persistent_session" {
  default = "false"
}

variable "simulation_step_interval_ms" {
  default = 2000
}

variable "connection_timeout_seconds" {
  default = 60
}

variable "subscribe_timeout_seconds" {
  default = 600
}

variable "keep_alive_seconds" {
  default = 180
}

variable "publish_qos" {
  default = 0
}

variable "subscribe_qos" {
  default = 0
}

