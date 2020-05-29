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
  default = "0.0.2-SNAPSHOT"
}

variable "node_count" {
  default = 3
}

variable "clients_per_node" {
  default = 1000
}

variable "message_min_size" {
  default = 300
}

variable "message_max_size" {
  default = 600
}

variable "ramp_up_seconds" {
  default = 120
}

variable "actions_during_ramp_up" {
  default = "false"
}

variable "simulation_step_interval_ms" {
  default = 2000
}

variable "client_step_interval_ms" {
  default = 10000
}

variable "connection_timeout_seconds" {
  default = 60
}

variable "keep_alive_seconds" {
  default = 180
}


###########################################
#############     DockerHub   #############
###########################################

variable "dockerhub_username" {
  type = string
}

variable "dockerhub_password" {
  type = string
}

