This module prepares the Docker build context for the `app` component in `target/docker/`
(the application tarball plus the filtered `Dockerfile.jvm` / `Dockerfile.mutable.jvm`).
Maven does not run `docker build` itself; the images are built by CI (see
`.github/workflows/verify-publish.yaml` and `release-images.yaml`) or manually:

```bash
# from the repository root; -Dfull is required for the mutable variant
./mvnw clean install -DskipTests -Pprod -Dfull

cd distro/docker/target/docker

# standard image (Quarkus fast-jar)
docker build -f Dockerfile.jvm -t apicurio/apicurio-registry:[project version] .

# re-augmentable image (Quarkus mutable-jar), see docs "Custom artifact types"
docker build -f Dockerfile.mutable.jvm -t apicurio/apicurio-registry:[project version]-mutable .
```

The `-mutable` image additionally contains `lib/deployment` and `/deployments/build.sh`, which
re-augments the application so that provider jars copied into `/deployments/quarkus-app/providers`
(for example Java implementations of custom artifact types) become part of the application:

```bash
docker run --rm apicurio/apicurio-registry:[project version]-mutable /deployments/build.sh --prune
```
