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

This setup contains a fully configured Apicurio Registry package already integrated with Keycloak. It contains a shell script which will configure the environment. Currently every application is routed to the host network without SSL support. This is a development version, do not use it in a production environment!

Here is the port mapping:
- 8090 for Keycloak
- 8080 for the Registry

#### Setup

The folder contains a bash script to make the initialization. The script will create the configuration files based on your IP address.
The scripts will create 3 files:
- .env
- config/keycloak/apicurio-realm.json

The easiest way is to run the script. At the end of the run, it will print the admin password for Keycloak, and the URLs for the services. Like this:

```
Keycloak username: admin
Keycloak password: op4oUQ

Keycloak URL: 192.168.1.231:8090
Apicurio URL: 192.168.1.231:8080

```

Please copy these values somewhere where you can find them easily!

#### Script based setup

```
./setup.sh {IP_OF_YOUR_HOST}
```

Note: make sure you use the external IP address of your host here.  `localhost` and `127.0.0.1` will not work.

#### Environment customisation

After the successfull run of the setup script, a file called `.env` will appear. This file contains the customisable properties of the environment. Every property is already filled in, so this is only for customization. You can set your passwords, URL's, and the versions of the components of Apicurio-Studio. The default version is the `latest`.

The passwords for DBs, KeyCloak, are generated dynamically with every run of the setup script.

If you want to change these settings (or the provided KeyCloak configuration) after you already started the stack, you have to remove the already existing docker volumes. The easiest way is to stop your running compose stack, and prune your volumes:

```
docker system prune --volumes
```

A simple "reset" script is also included, it will remove the generated config files for you.

```
./reset_env.sh
```

#### Starting the environment

Once your configs are generated, you can start the whole stack with these commands:

```
docker-compose -f docker-compose.keycloak.yml build
docker-compose -f docker-compose.keycloak.yml up
docker-compose -f docker-compose.apicurio.yml up
```

To clear the environment, please run these commands:

```
docker system prune --volumes
./reset_env.sh
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

