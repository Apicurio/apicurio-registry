This module builds the docker image for the `app` component, if activated by the `docker` property.

In case of an error while executing `docker` during the build, try:

`sudo chmod a+rw /var/run/docker.sock`

If you skip the docker execution, you can build the image manually:

`cd target/docker`

`docker build -f Dockerfile.jvm -t apicurio/apicurio-registry:[project version] .`
