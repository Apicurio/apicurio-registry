---
name: Bug report
about: Use this template to report a bug in this project
title: ""
labels: Bug
---

<!--

In order for us to better investigate the bug, please fill in as much relevant information as is reasonable in the format below.

Before proceeding, consider the following:

- This repository is for issues with Apicurio Registry. Make sure the bug report is not more appropriate or another repository.
- Make sure the bug is in Apicurio Registry and not in an external library or other non-Apicurio software.
 For example, we provide a compatibility REST API for Confluent Schema Registry clients. Make sure the bug is not in one of their libraries.
- Run the Registry with DEBUG logging enabled, by passing the LOG_LEVEL=DEBUG env. variable.

-->

## Description

**Registry
Version**: <!-- Please add the Apicurio Registry version number here. You can use "latest" for the latest released version, or "snapshot" if you are using a build from main. -->
**Persistence type**: <!-- Please add the persistence type here. One of "in-memory", "sql", or "kafkasql". -->

<!-- Please provide as much detail about the bug as possible. -->

### Environment

<!-- 
What is the environment in which you are running Apicurio Registry? e.g. Kubernetes v1.25. 
How are you interacting with Apicurio Registry? Are you using a specific serdes client? Please include its version.
-->

### Steps to Reproduce

<!--
Add the steps required to reproduce this error.

1. Go to `XX >> YY >> SS`
2. Create a new item `N` with the info `X`
3. See error

If the error cannot be easily reproduced, make sure to fill in the Environment and Logs sections.
You can also try to reproduce the bug with a fresh Apicurio Registry instance.
-->

### Expected vs Actual Behaviour

<!-- A clear and concise description of what you expected to happen and what actually happened. -->

### Logs

<!-- Paste or link the logs from Registry, browser console, your command line or other relevant places. Redact if needed.-->
