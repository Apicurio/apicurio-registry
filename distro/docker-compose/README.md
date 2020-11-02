# Docker Compose

The `./src/main/resources` directory contains several *Docker Compose* files. Some of them require others as a dependency.
You may need to create custom ones for your use-case, but these should provide simple examples 
on how to deploy *Apicurio Registry* (e.g. for local testing). For more complicated deployments use the Kubernetes operator (*WIP*) 
or the documentation.

Building of this module produces a zip archive artifact with the *Docker Compose* files 
and related configuration.

## Prerequisites

Install *Docker* and *Docker Compose*. You can either build *Apicurio Registry* images locally 
(see the build documentation in the project root), 
or use the pre-built images from a public registry.

In order to provide configuration files to the containers, create a *config* docker volume `docker volume create config` 
and copy the content of the `./config` directory to the volume - `docker volume inspect config | jq -r '.[0].Mountpoint'`.

## Use-cases

### Metrics with Prometheus and Grafana

Run `compose-metrics.yaml` together with a base compose file, e.g. 
`docker-compose -f compose-metrics.yaml -f compose-base-sql.yaml up --abort-on-container-exit`.

*Grafana* console should be available at `http://localhost:3000` after logging in as *admin/password*.
