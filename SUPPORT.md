# Getting help with Apicurio Registry

Thanks for using Apicurio Registry! This page explains where to get help and where each kind
of question or report belongs, so you get an answer as quickly as possible and the next person
can find it too.

A quick search of the [documentation](https://www.apicur.io/registry/docs/) and the existing
[Q&A discussions](https://github.com/Apicurio/apicurio-registry/discussions/categories/q-a)
answers many common questions.

## Where should I go?

| What you need | Where to go |
|---|---|
| Ask a usage or "how do I..." question | The [#apicurio channel on CNCF Slack](https://cloud-native.slack.com/archives/C0BDWTC1DTM) for a quick chat, or [GitHub Discussions -> Q&A](https://github.com/Apicurio/apicurio-registry/discussions/categories/q-a) so the answer stays searchable |
| Report a bug | [Open an issue](https://github.com/Apicurio/apicurio-registry/issues/new/choose) using the **Bug report** template |
| Propose or discuss a feature or idea | Float it in [GitHub Discussions -> Ideas](https://github.com/Apicurio/apicurio-registry/discussions/categories/ideas) first, then open a [Feature request](https://github.com/Apicurio/apicurio-registry/issues/new/choose) once it is concrete |
| Report a security vulnerability | Do **not** open a public issue. Follow the private disclosure process in [SECURITY.md](SECURITY.md) |
| Contribute code or documentation | See [CONTRIBUTING.md](CONTRIBUTING.md) and [DEVELOPING.md](DEVELOPING.md) |
| Read the documentation | The [Apicurio Registry documentation](https://www.apicur.io/registry/docs/) |

New to CNCF Slack? Join at [slack.cncf.io](https://slack.cncf.io) first, then open the
[#apicurio channel](https://cloud-native.slack.com/archives/C0BDWTC1DTM).

## Before you ask

You will get a faster, better answer if you:

- Search the [documentation](https://www.apicur.io/registry/docs/) and the
  [existing discussions](https://github.com/Apicurio/apicurio-registry/discussions) first, since
  most questions have already been answered.
- Include your **Apicurio Registry version**, your **persistence type** (`in-memory`, `sql`, or
  `kafkasql`), and how you are deploying, so people can help without a round of back-and-forth.

## Frequently asked questions

These are the questions that come up most often. The answers link to the authoritative
documentation so they stay current.

**What is Apicurio Registry?**

A registry for storing, managing, and validating API definitions and schemas (Avro, Protobuf,
JSON Schema, OpenAPI, and more). It is a CNCF Sandbox project. See the
[documentation](https://www.apicur.io/registry/docs/).

**How do I install, run, or try it?**

The quick start and the deployment options are in the
[documentation](https://www.apicur.io/registry/docs/). To build from source, see
[DEVELOPING.md](DEVELOPING.md).

**Which storage or persistence type should I use?**

Registry supports several, selected at runtime: `in-memory` (for trying it out), `sql` (a
relational database such as PostgreSQL), and `kafkasql` (backed by Kafka). Configuration is
covered in the [documentation](https://www.apicur.io/registry/docs/).

**How do I use it with my Kafka producers and consumers?**

Through the Apicurio Registry SerDes (serializer and deserializer) libraries. See the
[documentation](https://www.apicur.io/registry/docs/).

**Is it compatible with Confluent Schema Registry?**

Yes. Registry exposes a compatibility REST API for Confluent Schema Registry clients. See the
[documentation](https://www.apicur.io/registry/docs/).

**How do I run it on Kubernetes?**

There is an Apicurio Registry Operator and container images; the deployment guides are in the
[documentation](https://www.apicur.io/registry/docs/).

**Where are the REST API and client SDK references?**

In the [documentation](https://www.apicur.io/registry/docs/).

## Contributing

Want to fix a bug, add a feature, or improve the docs? Start with
[CONTRIBUTING.md](CONTRIBUTING.md), which covers picking and claiming an issue, the build and
review process, and where to ask contributor questions. Build and IDE setup live in
[DEVELOPING.md](DEVELOPING.md).

## Community and Code of Conduct

Apicurio Registry is a [Cloud Native Computing Foundation](https://cncf.io) Sandbox project.
Everyone interacting in the project's spaces is expected to follow the
[CNCF Code of Conduct](CODE_OF_CONDUCT.md). Please be kind and respectful; we are glad you are here.
