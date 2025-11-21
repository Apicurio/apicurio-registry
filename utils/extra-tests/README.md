# Extra Tests

This is a set of tests that are, for one reason or another, not included in the integration tests module.

### Prerequisites:

- **Docker** must be running.
- No other services need to be started manually - the tests handle everything!

### How to run:

First, build the Apicurio Registry Docker image:

```bash
mvn clean install -pl distro -am -DskipTests
pushd distro/docker/target/docker && docker build -f Dockerfile.jvm -t quay.io/apicurio/apicurio-registry:snapshot . && popd
```

Then run the test with:

```bash
mvn verify -pl utils/extra-tests -am -DskipUTs -Pextra-tests -Dregistry3-image=quay.io/apicurio/apicurio-registry:snapshot
```

### Note:

The tests are disabled by default to prevent them from running during normal builds. Run the tests by enabling the `-Pextra-tests` profile.
