[![Verify Build Workflow](https://github.com/Apicurio/apicurio-registry/workflows/Verify%20Build%20Workflow/badge.svg)](https://github.com/Apicurio/apicurio-registry/actions?query=workflow%3A%22Verify+Build+Workflow%22)
[![Join the chat at https://apicurio.zulipchat.com/](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://apicurio.zulipchat.com/)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry?ref=badge_shield)

![Apicurio Registry](.assets/apicurio_registry_logo_default.svg)

An API/Schema registry - stores and retrieves APIs and Schemas.

## Table of Contents

- [Quick Start](#quick-start)
- [Running with Docker](#running-with-docker)
- [Documentation](#documentation)
- [Community](#community)
- [License](#license)

## Quick Start

Build the project and run the registry with the in-memory storage variant:

 ```
 ./mvnw clean install -Dlocal -DskipTests
 cd app/
 ../mvnw quarkus:dev
 ```

This should result in Quarkus and the in-memory registry starting up, with the REST APIs available on localhost port 8080:

* [API documentation](http://localhost:8080/apis)

To start the user interface in development mode, hosted on port 8888 of your localhost:

```
cd ui
npm install
cd ui-app
./init-dev.sh
npm run dev
```

* [User Interface](http://localhost:8888)

For more information on the UI, see the UI module's [README.md](ui/README.md).

## Running with Docker

Pre-built container images are published to Docker Hub for every commit to `main`.
Run the [registry](https://hub.docker.com/r/apicurio/apicurio-registry) image:

    docker run -it -p 8080:8080 apicurio/apicurio-registry:latest-snapshot

To also run the [user interface](https://hub.docker.com/r/apicurio/apicurio-registry-ui), start its image as well:

    docker run -it -p 8888:8080 apicurio/apicurio-registry-ui:latest-snapshot

Once both are running you can access:

* [API documentation](http://localhost:8080/apis)
* [User Interface](http://localhost:8888)

For storage, security, and other runtime configuration options — and the full list of
available image tags — see the [documentation](https://www.apicur.io/registry/docs/).

## Documentation

- Build setup, IDE configuration, and testing → [DEVELOPING.md](DEVELOPING.md)
- Contribution guidelines and versioning policy → [CONTRIBUTING.md](CONTRIBUTING.md)
- Runtime configuration, security, and deployment → [full documentation](https://www.apicur.io/registry/docs/)

## Community

Apicurio Registry is a [Cloud Native Computing Foundation](https://cncf.io) Sandbox project.

We abide by the [CNCF Code of Conduct](CODE_OF_CONDUCT.md).

---

Copyright Apicurio Registry a Series of LF Projects, LLC.
For web site terms of use, trademark policy and other project policies please see https://lfprojects.org/policies/.

## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry?ref=badge_large)
