# Model Context Protocol Server for Apicurio Registry

This Model Context Protocol (MCP) server enables Large Language Models (LLMs) to interact with an Apicurio Registry
server.

More information, see:

- [Introduction to MCP](https://modelcontextprotocol.io/introduction)
- [Quarkus MCP server extension](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html)
- [Quarkus MCP server examples](https://github.com/quarkiverse/quarkus-mcp-servers)

## Quickstart

1. Use docker to run Apicurio Registry 3:

  ```shell
  # API server
  docker run --rm -it -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot
  # UI server
  docker run --rm -it -p 8888:8080 quay.io/apicurio/apicurio-registry-ui:latest-snapshot
  ```

2. Register for [Claude AI](https://claude.ai).

3. Install the [Claude Desktop](https://claude.ai/download) application. If you are using a system that is not
   officially supported, like Fedora, [unofficial installation options](https://github.com/bsneed/claude-desktop-fedora)
   are available.

4. Run Claude Desktop, and go to *File* > *Settings...* > *Developer* > *Edit Config*, to open the configuration file
   (e.g. `~/.config/Claude/claude_desktop_config.json`).

5. Update the configuration file as follows:

  ```json
  {
  "mcpServers": {
    "Apicurio Registry (docker)": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "--network=host",
        "quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot"
      ]
    }
  }
}
  ```

### Build

1. Instead of using a pre-built docker image, you can build the Apicurio Registry MCP server yourself (from this
   directory):

   ```shell
   mvn clean install -f ../pom.xml -pl mcp -am -DskipTests
   ```

2. Update the configuration file as follows:

  ```json
  {
  "mcpServers": {
    "Apicurio Registry (jar)": {
      "command": "java",
      "args": [
        "-jar",
        "(...)/apicurio-registry/mcp/target/apicurio-registry-mcp-server-3.0.10-SNAPSHOT-runner.jar"
      ]
    }
  }
}
  ```

You can build the docker image as well:

```shell
cd target/docker
docker build -f Dockerfile.jvm -t quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot
```

### Configuration

The following configuration properties can be provided in the `args` list:

- JAR: Place them before `-jar`, e.g. `"-Dregistry.url=localhost:8080",`
- Docker: Place them before the docker image name as env. variables using two separate elements,
  e.g. `"-e", "REGISTRY_URL=localhost:8080",`.

| Property                        | Default value    | Description                                                                                                                                                                                                                      |
                    |---------------------------------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| registry.url                    | `localhost:8080` | URL of the Apicurio Registry 3 server.                                                                                                                                                                                           |
| apicurio.mcp.safe-mode          | `true`           | Prevent some operations from being performed, or filter which operations can be performed, for safety reasons.                                                                                                                   |
| apicurio.mcp.paging.limit       | `200`            | Claude does not do well with paging, therefore we use a high limit parameter when paged results are returned. Increase the limit if your server contains large amount of objects, and you do not mind the higher operation cost. |
| apicurio.mcp.paging.limit-error | `true`           | Fail the operation when there number of result exceeds the paging limit. This is guard against inaccurate results, e.g. counting.                                                                                                |

## Usage

Ask Claude a question about Apicurio Registry, or to perform a task, for example:

- What is the version of the Registry server?
- What groups are present in the Registry?
- What is the latest artifact version that has been added to the Registry?
- Describe changes between the versions in the audit-log-event artifact.
- Which groups have the environment=production label?
- Create 10 example artifacts.

*NOTE: The search and list operations might take more time if there is a large number of objects in the Registry. This
is an area of future improvement of the MCP server (e.g. caching, better search functions).*

The MCP server currently supports the following operations:

- Artifact types - list
- Configuration properties - get, list, update*
- Groups - create, get, list, search, update
- Artifacts - create, get, list, search, update
- Versions - create, get, list, search, update

*(\*) Some operations are restricted for safety reasons.*

Clause Desktop will prompt you before it executes a tool call.

In addition to the function calls available to Claude, the MCP server also provide several prepared "system" prompts:

- `create_new_artifact_version`

These provide more context for a particular task, and behavior rules for Claude. To use a system prompt, click on the *
*+** icon, and select **Add from Apicurio Registry**.

8. When you rebuild the MCP server code, you need to restart Claude Desktop with *File* > *Exit* and reopening.

## Troubleshooting

### Get more logging

To get more detailed logging you can add the following parameters to the `args` list:

```
// ...
"args": [
  // ...
  "-e", "QUARKUS_LOG_FILE_ENABLE=true",
  "-e", "QUARKUS_LOG_FILE_PATH=/mnt/log/mcp-server.log",
  "-v", "(...)/apicurio-registry/mcp:/mnt/log",
  "quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot"
]
```

or

```
// ...
"args": [
  "-Dquarkus.log.file.enable=true",
  "-Dquarkus.log.file.path=(...)/apicurio-registry/mcp/mcp-server.log"
  // ...
  "-jar",
  "(...)/apicurio-registry/mcp/target/apicurio-registry-mcp-server-3.0.10-SNAPSHOT-runner.jar"
]
```
