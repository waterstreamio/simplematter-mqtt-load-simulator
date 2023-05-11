# mqtt-load-simulator

Simulate load on MQTT broker.

When started the simulator ramps up client number during `MQTT_LOAD_RAMP_UP_SECONDS` seconds (defaut is 10)
until it reaches `MQTT_LOAD_MIN_CLIENTS` (default is 2) clients. 
After that clients start and stop randomly to keep its number between `MQTT_LOAD_MIN_CLIENTS` 
and `MQTT_LOAD_MAX_CLIENTS` (default is 2) or stay constant if these 2 parameters are equal.

Once connected, each client does a random action 
(publish message, subscribe to the topic, unsubscribe from the topic, do nothing)
each `MQTT_LOAD_CLIENT_STEP_INTERVAL` milliseconds (2000 by default).
Probabilities are controlled by `MQTT_LOAD_CLIENT_STEP_INTERVAL`, 
`MQTT_LOAD_CLIENT_STEP_INTERVAL`, `MQTT_LOAD_CLIENT_STEP_INTERVAL` and `MQTT_LOAD_CLIENT_IDLE_PROBABILITY`.
Publish message payload is a random text of the dictionary words. Its length is randomly picked between 
`MQTT_LOAD_MESSAGE_MIN_SIZE` (default is 10) and `MQTT_LOAD_MESSAGE_MAX_SIZE` (default is 600) bytes.

Prometheus metrics are exposed on port `MQTT_LOAD_MONITORING_PORT` (default is 1884).
For complete list of configuration parameters check `src/main/resources/application.conf`.

## Run locally as a Docker container

Pre-requisites:
    - Docker

Copy `toolbelt/runDocker.sh`, customize the parameters you want and run it.


## Run on Google Cloud Platform

Pre-requisites:
- GCP account
- [Terraform (0.12)](https://www.terraform.io/downloads.html) or newer installed locally
    
1. Download `account.json` from GCP Account into `toolbelt/terraform_gcp` directory. 
You will have to create a [service account on GCP](https://cloud.google.com/iam/docs/creating-managing-service-account-keys) first if you don't have one. 
Choose the right roles and enable google API. If something is missing terraform let you know at a later steps and allow to retry after adding necessary roles. 
Once you have a Service Account, you can get `account.json` this way: 
    * Open GCP Console in the web browser 
    * Go to "IAM & admin / Service Accounts" section
    * Click "Actions / Create Key" for the specific service account, choose JSON key
    * Download .json file, rename it to `account.json`
    * Copy to terraform-gcp and terraform-gcp-ccloud directories`
2. Choose a `GCP project` or create a new one on your cloud console. 
Terraform will prompt you to specify your project name when applying.
Write down the following information, you will need it in the next steps:
    * your GCP project name
    * your GCP region
    * your GCP zone
3. Copy configuration file example:

```
cd toolbelt/terraform_gcp
cp config-examples/config.auto.tfvars.example ./config.auto.tfvars
```
4. Customize config file `config.auto.tfvars` with the GCP project parameters, MQTT broker connection data and desired load settings 
5. Run it with `toolbelt/terraform_gcp/apply.sh`. Upon successful start it will print the URL of the Grafana dashboard.
Default credentials are "admin/admin"
6. When done - run `toolbelt/terraform_gcp/destroy.sh` to stop it

## Run on Kubernetes

Pre-requisistes:
- Kubernetes cluster
- `kubectl` installed locally, configured with your k8s cluster credentials

1. Copy config file example:
```
cd toolbelt/k8s
cp config.sh example config.sh
```
2. Customize config file with MQTT broker location and desired load settings
3. Run the load simulator with `toolbelt/k8s/apply.sh`
4. When done - run `toolbelt/k8s/undeploy.sh` to stop it.

