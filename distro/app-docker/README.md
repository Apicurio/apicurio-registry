This module builds docker image(s) for the `app` component.

If you activate the `native` Maven profile, both standard and native image is produced.

In case of an error while executing `docker` during the build, try:

`sudo chmod a+rw /var/run/docker.sock`

You can skip the docker execution by setting the `docker.skip` property 
and build the image manually:

`cd target/docker`

`docker build -f Dockerfile.jvm -t apicurio/apicurio-registry:[project version] .`

or

`docker build -f Dockerfile.native -t apicurio/apicurio-registry:[project version]-native .`
