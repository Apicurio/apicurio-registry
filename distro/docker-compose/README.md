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



### Docker-compose and Quarkus based installation

#### Overview

This setup contains a fully configured Apicurio Registry package already integrated with Keycloak. Currently every application is routed to the host network without SSL support. This is a development version, do not use it in a production environment!

Here is the port mapping:
- 8080 for Keycloak
- 8081 for the Registry

#### Starting the environment

You can start the whole stack with these commands:

```
docker-compose -f docker-compose.apicurio.yml up
```

To clear the environment, please run these commands:

```
docker system prune --volumes
```

#### Configure users in Keycloak

The Keycloak instance is already configured, you don't have to create the realms manually.

At the first start there are no default users added to Keycloak. Please navigate to:
`http://YOUR_IP:8090`

The default credentials for Keycloak are: `admin` and the password can be found in the previously generated `.env` file, under `KEYCLOAK_PASSWORD`.

Select Registry realm and add a user to it. You'll need to also assign the appropriated role.


#### Login to Apicurio and Keycloak

Apicurio URL: `http://YOUR_IP:8080`
Keycloak URL: `http://YOUR_IP:8090`

