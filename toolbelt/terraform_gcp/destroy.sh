#!/bin/sh
set -e

echo Destroying MQTT Load Simulator deploy
terraform destroy --auto-approve
