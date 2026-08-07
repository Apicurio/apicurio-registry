[![Verify Build Workflow](https://github.com/Apicurio/apicurio-registry/workflows/Verify%20Build%20Workflow/badge.svg)](https://github.com/Apicurio/apicurio-registry/actions?query=workflow%3A%22Verify+Build+Workflow%22)
[![Join the chat on CNCF Slack](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://cloud-native.slack.com/archives/C0BDWTC1DTM)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry?ref=badge_shield)

![Apicurio Registry](.assets/apicurio_registry_logo_default.svg)

An API/Schema registry - stores and retrieves APIs and Schemas.

## Quick Start

Build the project and run the registry with the in-memory storage variant:

**Build requirement:** JDK 21 or newer is required to build the project (the build tooling, e.g. Checkstyle, needs a Java 21+ runtime). The produced artifacts still target Java 17.

 ```
 ./mvnw clean install -Dlocal -DskipTests
 cd app/
 ../mvnw quarkus:dev -Dlocal
 ```

(See [DEVELOPING.md](DEVELOPING.md#build-tiers) for build tier details and other options.)

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

For the full set of runtime configuration options (storage, deployment, and more), see the
[documentation](https://www.apicur.io/registry/docs/). For the available image tags and the
support policy, see [Versioning & Support Policy](#versioning--support-policy) below; for
authentication, see [Security](#security).

## Versioning & Support Policy

Apicurio Registry follows [Semantic Versioning](https://semver.org/):

- **Minor releases** (3.3.0, 3.4.0, ...): new features, enhancements, and bug fixes.
- **Patch releases** (3.3.1, 3.3.2, ...): CVE and security fixes only. No new features, no bug fixes.

**Support window:** the two most recent minor versions (latest and latest-1) receive patch releases for security issues. Older minors are end-of-life.

**Docker image tags:**

| Tag | Description |
|-----|-------------|
| `3.3.0` | Pinned to an exact release |
| `3.3` | Floating tag — always points to the latest patch in the 3.3.x series |
| `latest` / `latest-release` | Always points to the most recent stable release |
| `latest-snapshot` | Most recent build from the `main` branch (unstable) |

**OLM channels (Kubernetes operator):** each minor version has its own OLM channel (e.g., `3.3.x`). Subscribe to a channel to receive only patch updates within that minor. A rolling `3.x` channel is also available for users who always want the latest minor.

## Security

You can enable authentication for both the REST APIs and the user interface using an OpenID
Connect (OIDC) server. The same server and users are federated across the UI and the REST APIs,
so a single set of credentials works for both. Set the following environment variables to enable it.

**REST API:**

| Env. variable                  | Description                                  |
|--------------------------------|----------------------------------------------|
| `QUARKUS_OIDC_TENANT_ENABLED`  | Set to `true` to enable (default is `false`) |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | OIDC server URL                              |
| `QUARKUS_OIDC_CLIENT_ID`       | The client for the API                       |

**User interface:**

| Env. variable                | Description                       |
|------------------------------|-----------------------------------|
| `REGISTRY_AUTH_TYPE`         | Set to `oidc` (default is `none`) |
| `REGISTRY_AUTH_URL`          | OIDC auth URL                     |
| `REGISTRY_AUTH_REDIRECT_URL` | OIDC redirect URL                 |
| `REGISTRY_AUTH_CLIENT_ID`    | The client for the UI             |

Everything must be configured in your OIDC provider before starting the application. Registry
supports a much wider range of authentication and authorization options than shown here — treat
this as a starting point and see the
[security documentation](https://www.apicur.io/registry/docs/) for the full picture.

## Documentation

- Build setup, IDE configuration, and testing → [DEVELOPING.md](DEVELOPING.md)
- Contribution guidelines → [CONTRIBUTING.md](CONTRIBUTING.md)
- Runtime configuration, security, and deployment → [full documentation](https://www.apicur.io/registry/docs/)

## Community

Apicurio Registry is a [Cloud Native Computing Foundation](https://cncf.io) Sandbox project.

Join us on the [#apicurio channel](https://cloud-native.slack.com/archives/C0BDWTC1DTM) on CNCF Slack.

We abide by the [CNCF Code of Conduct](CODE_OF_CONDUCT.md).

---

Copyright Apicurio Registry a Series of LF Projects, LLC.
For web site terms of use, trademark policy and other project policies please see https://lfprojects.org/policies/.

## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FApicurio%2Fapicurio-registry?ref=badge_large)
