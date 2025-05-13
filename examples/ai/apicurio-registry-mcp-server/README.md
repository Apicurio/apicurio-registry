# Model Context Protocol Server for Apicurio Registry

This Model Context Protocol (MCP) server enables Large Language Models (LLMs) to interact with an Apicurio Registry
server.

More information, see:

- [Introduction to MCP](https://modelcontextprotocol.io/introduction)
- [Quarkus MCP server extension](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html)
- [Quarkus MCP server examples](https://github.com/quarkiverse/quarkus-mcp-servers)

## Quickstart

1. A running instance of Apicurio Registry 3 is required. You can use Docker:

```shell
# API server
docker run --rm -it -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot
# UI server
sudo docker run --rm -it -p 8888:8080 quay.io/apicurio/apicurio-registry-ui:latest-snapshot
```

1. Register for [Claude AI](https://claude.ai).

2. Install the [Claude Desktop](https://claude.ai/download) application. If you are using a system that is not
   officially supported, like Fedora, [unofficial installation options](https://github.com/bsneed/claude-desktop-fedora)
   are available.

3. Build the Apicurio Registry MCP server (from this directory):

   ```shell
   mvn clean install -f ../../../pom.xml -pl examples/ai/apicurio-registry-mcp-server -am -Pexamples -DskipTests
   ```

4. Run Claude Desktop, and go to *File* > *Settings...* > *Developer* > *Edit Config*, to open the configuration file
   (e.g. `~/.config/Claude/claude_desktop_config.json`).

5. Update the configuration file as follows:

   ```json
   {
     "mcpServers": {
       "Apicurio Registry": {
         "command": "java",
         "args": [
           "-jar",
           "(...)/apicurio-registry/examples/ai/apicurio-registry-mcp-server/target/apicurio-registry-mcp-server-0.0.1-SNAPSHOT-runner.jar"
         ]
       }
     }
   }
   ```

   The following configuration properties can be provided in the `args` list (place them before `-jar`,
   e.g. `-Dregistry.url=localhost:8080`):

   | Property                        | Default value    | Description                                                                                                                                                                                                                      |
                    |---------------------------------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
   | registry.url                    | `localhost:8080` | URL of the Apicurio Registry 3 server.                                                                                                                                                                                           |
   | apicurio.mcp.safe-mode          | `true`           | Prevent some operations from being performed, or filter which operations can be performed, for safety reasons.                                                                                                                   |
   | apicurio.mcp.paging.limit       | `200`            | Claude does not do well with paging, therefore we use a high limit parameter when paged results are returned. Increase the limit if your server contains large amount of objects, and you do not mind the higher operation cost. |
   | apicurio.mcp.paging.limit-error | `true`           | Fail the operation when there number of result exceeds the paging limit. This is guard agains inaccurate results, e.g. counting.                                                                                                 |

6. Ask Claude a question about Apicurio Registry, or to perform a task, for example:

    - `What is the version of the Registry server?`
    - `What groups are present in the Registry?`
    - `What is the latest artifact version that has been added to the Registry?`
    - `Describe changes between the versions in the audit-log-event artifact.`
    - `Which groups have the environment=production label?`
    - `Create 10 example artifacts.`

   *NOTE: The search and list operations might take more time if there is a large number of objects in the Registry.
   This is an area of future improvement of the MCP server (e.g. caching, better search functions).*

   The MCP server currently supports the following operations:

    - Artifact types - list
    - Configuration properties - get, list, update*
    - Groups - create, get, list, search, update
    - Artifacts - create, get, list, search, update
    - Versions - create, get, list, search, update

   *(\*) Some operations are restricted for security reasons.*

   Clause Desktop will prompt you before it executes a tool call.

7. In addition to the function calls available to Claude, the MCP server also provide several prepared "system" prompts:

    - `create_new_artifact_version`

   These provide more context for a particular task, and behavior rules for Claude. To use a system prompt, click on the
   **+** icon, and select **Add from Apicurio Registry**.

8. When you rebuild the MCP server code, you need to restart Claude Desktop with *File* > *Exit* and reopening.

## Troubleshooting

### Get more logging

To get more detailed logging you can add the following parameters to the `args` list:

```
// ...
"args": [
  "-Dquarkus.log.file.enable=true",
  "-Dquarkus.log.file.path=/path/to/the/repository/mcp-server.log"
  // ...
  "-jar",
  "(...)/apicurio-registry/examples/ai/apicurio-registry-mcp-server/target/apicurio-registry-mcp-server-0.0.1-SNAPSHOT-runner.jar"
]
```

## Future Improvements

MCP server technology provides a lot of opportunity for experimentation! We welcome and appreciate any contributions!
Some of the things that could be improved:

- Support more Apicurio REST API calls, or additional functionality.
- Add caching
- Create a version of the MCP server that executes SQL queries directly against Apicurio Registry database. This is less
  safe, but it could be limited only to the SELECT queries, which would result in a much faster data retrieval than the
  REST API calls.
- Create a version that uses Apicurio Registry API schema and a generic HTTP client to execute REST API calls.
- Integrate the MCP server with a (future) Apicurio Registry CLI
