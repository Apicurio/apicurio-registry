# Contributing guide

**Want to contribute? Great!** 
We try to make it easy, and all contributions, even the smaller ones, are more than welcome.
This includes bug reports, fixes, documentation, examples... 
But first, read this page (including the small print at the end).

* [Legal](#legal)
* [Reporting an issue](#reporting-an-issue)
* [Before you contribute](#before-you-contribute)
  + [Code reviews](#code-reviews)
  + [Coding Guidelines](#coding-guidelines)
  + [Continuous Integration](#continuous-integration)
  + [Tests and documentation are not optional](#tests-and-documentation-are-not-optional)
* [The small print](#the-small-print)


## Legal

All original contributions to Apicurio projects are licensed under the
[ASL - Apache License](https://www.apache.org/licenses/LICENSE-2.0),
version 2.0 or later, or, if another license is specified as governing the file or directory being
modified, such other license.

All contributions are subject to the [Developer Certificate of Origin (DCO)](https://developercertificate.org/).
The DCO text is also included verbatim in the [dco.txt](dco.txt) file in the root directory of the repository.

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing and what you would expect to see.
Don't forget to indicate your Apicurio Registry, Java, and Maven versions.

## Before you contribute

To contribute, use GitHub Pull Requests, from your **own** fork.

Also, make sure you have set up your Git authorship correctly:

```
git config --global user.name "Your Full Name"
git config --global user.email your.email@example.com
```

If you use different computers to contribute, please make sure the name is the same on all your computers.

We may use this information to acknowledge your contributions!

### Code reviews

All submissions, including submissions by project members, need to be reviewed by at least one Apicurio committer before being merged.

[GitHub Pull Request Review Process](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews) is followed for every pull request.

### Coding Guidelines

 * We primarily use the Git history to track authorship. GitHub also has [this nice page with your contributions](https://github.com/quarkusio/quarkus/graphs/contributors).
 * Please take care to write code that fits with existing code styles.  For your convenience we have Formatters and/or Code Templates for both [Eclipse](https://github.com/Apicurio/apicurio-configs/tree/main/eclipse) and [IntelliJ](https://github.com/Apicurio/apicurio-configs/tree/main/intellij).
 * Commits should be atomic and semantic. Please properly squash your pull requests before submitting them. Fixup commits can be used temporarily during the review process but things should be squashed at the end to have meaningful commits.
 * We typically squash and merge pull requests when they are approved.  This tends to keep the commit history a little bit more tidy without placing undue burden on the developers.

### Continuous Integration

Because we are all humans, and to ensure Apicurio Registry is stable for everyone, all changes must pass continuous integration before being merged. Apicurio CI is based on GitHub Actions, which means that pull requests will receive automatic feedback.  Please watch out for the results of these workflows to see if your PR passes all tests.

### Tests and documentation are not optional

Don't forget to include tests in your pull requests. 
Also don't forget the documentation (reference documentation, javadoc...).

Be sure to test your pull request using all storage variants:

1. SQL storage (using the `-Psql` profile)
2. KafkaSQL storage (using the `-Pkafkasql` profile)

### Customizing Registry supported ArtifactTypes

Apicurio Registry is a modular project and it allows re-use of the produced artifacts in order to extend and enanche the functionalities.

More specifically it's possible to modify the currently supported artifact types and add new onces by providing an higher priority  `io.apicurio.registry.types.bigquery.provider.ArtifactTypeUtilProviderImpl` to the DI framework.

In [this repository](https://github.com/andreaTP/apicurio-registry-with-bigquery-example) you can find an example where we add a demo BigQuery support.

Those are the interesting parts:

 - use Apicurio Registry [as a dependency](https://github.com/andreaTP/apicurio-registry-with-bigquery-example/blob/66c5d18d9c0b5e246597b79e5c5b82a54752a65d/pom.xml#L45-L49)
 - provide [an higher priority `ArtifactTypeUtilProviderImpl`](https://github.com/andreaTP/apicurio-registry-with-bigquery-example/blob/66c5d18d9c0b5e246597b79e5c5b82a54752a65d/src/main/java/io/apicurio/registry/types/bigquery/provider/ArtifactTypeUtilProviderImpl.java#L30-L33)
 - tweak [the provider list](https://github.com/andreaTP/apicurio-registry-with-bigquery-example/blob/66c5d18d9c0b5e246597b79e5c5b82a54752a65d/src/main/java/io/apicurio/registry/types/bigquery/provider/ArtifactTypeUtilProviderImpl.java#L48) in the constructor to include the additional artifact type

**NOTES:**

- when creating an artifact of a type not included in the default you ALWAYS need to specify the appropriate artifact type
- the UI will show the plain name of the additional type and won't have an appropriate icon to identify it

## The small print

This project is an open source project, please act responsibly, be nice, polite and enjoy!

