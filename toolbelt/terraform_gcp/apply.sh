#!/bin/sh
set -e

echo Applying MQTT Load Simulator group
terraform apply -target google_compute_instance_group_manager.mqtt_loadsim_group --auto-approve

echo Applying remaining resources
terraform apply --auto-approve
