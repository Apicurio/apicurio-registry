# CLI for Apicurio Registry

> NOTE: The CLI is a dev-preview project, and some features of Apicurio Registry are not supported yet. Apicurio Registry CLI is currently unstable; its arguments and behavior are subject to backwards-incompatible changes.

### Supported Platforms

The CLI is distributed as a native executable. A separate ZIP is provided for each platform:

| Platform | Architecture            | ZIP Classifier | Shell |
|----------|-------------------------|----------------|-------|
| Linux    | x86_64                  | `linux-x86_64` | bash  |
| macOS    | aarch64 (Apple Silicon) | `osx-aarch64`  | zsh   |

Windows is not supported.

## Installation

Prerequisites:

 - Linux (x86_64) with bash, or macOS (Apple Silicon) with zsh

To install the Apicurio Registry CLI:

1. Download the ZIP for your platform from [GitHub Releases](https://github.com/Apicurio/apicurio-registry/releases) or [Maven Central](https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-cli).
2. Unzip the downloaded file to a location of your choice.
3. You can run the CLI directly using `./acr`, or install it for the local user first (recommended):

   1. Run `./acr install` to install the CLI. This will install the CLI files to default locations (`$HOME/bin` and `$HOME/.apicurio/apicurio-registry-cli`), update the `~/.bashrc` file (Linux) or `~/.zshrc` file (macOS), and configure shell completions. Global installation is not supported yet.
4. If you do not have an instance of Apicurio Registry running, you use Docker:

   ```bash
   # Backend
   docker run --rm -it -p 8080:8080 -e APICURIO_REST_DELETION_GROUP_ENABLED=true \
     quay.io/apicurio/apicurio-registry:latest-snapshot
   # UI
   docker run --rm -it -p 8888:8080 quay.io/apicurio/apicurio-registry-ui:latest-snapshot
   ```


### Update

The CLI checks for updates once per day and notifies you when a newer version is available.

**Update to the latest version:**
```bash
acr update
```

If both a patch and minor update are available, you must specify the version:
```bash
acr update <version>
```

**Check for updates without installing:**
```bash
acr update --check
```

**Postpone update notifications (default: 5 days):**
```bash
acr update --postpone
acr update --postpone <hours>
```

**Install from a local ZIP:**
```bash
acr update --path <zip-file-path>
```

**Disable automatic update checks:**
```bash
acr config set update.check-enabled=false
```

## Build

The CLI is distributed as a native executable. The build produces an architecture-specific ZIP
containing the native binary, shell scripts, and completions. **Native build is the default** —
this is what you need to test or install the CLI locally.

```bash
mvn clean package -pl cli -am -DskipTests
```

The output ZIP will be at `cli/target/apicurio-registry-cli-*-<os>-<arch>.zip`.

By default, the native build uses the local GraalVM installation (set `GRAALVM_HOME` or have
`native-image` on your PATH). The system must also have `gcc` and the `zlib` static library (or `zlib-ng-compat-static`) installed:

```bash
# Fedora/RHEL/CentOS
sudo dnf install gcc zlib-static

# Debian/Ubuntu
sudo apt install gcc zlib1g-dev
```

To use a Mandrel container image instead (no local GraalVM or native toolchain needed, requires Docker/Podman):

```bash
mvn clean package -pl cli -am -DskipTests -Dquarkus.native.container-build=true
```

#### Skipping Native Build (Development Only)

During development and testing of CLI code (not the binary itself), you can skip the native
compilation to speed up the build. This produces a JVM-mode Quarkus application only —
**no installable ZIP is created**:

```bash
mvn clean package -pl cli -am -DskipTests -DcliSkipNative
```

#### GraalVM Version Compatibility

The **local build** (default) requires GraalVM CE or Mandrel for JDK 17 or later. Set `GRAALVM_HOME` to point to the installation.

The **container-based build** (`-Dquarkus.native.container-build=true`) uses a Mandrel image and works out of the box — no local GraalVM or native toolchain needed.

### Installation from Build

If you have not already installed the CLI, run:

```bash
# On Linux (bash):
unzip cli/target/apicurio-registry-cli-*.zip -d cli/target/cli && (pushd cli/target/cli && ./acr install ; popd) && source ~/.bashrc

# On macOS (zsh):
unzip cli/target/apicurio-registry-cli-*.zip -d cli/target/cli && (pushd cli/target/cli && ./acr install ; popd) && source ~/.zshrc
```

If you have already installed the CLI, run:

```
acr update --path cli/target/apicurio-registry-cli-*.zip
```

## Usage

The Apicurio Registry CLI (`acr`) provides commands to interact with Apicurio Registry from the command line.

### Getting Help

View available commands:
```bash
acr --help
```

Get help for a specific command:
```bash
acr <command> --help
acr <command> <subcommand> --help
```

### Configuration

Manage CLI configuration properties stored in `config.json`.

**List all properties:**
```bash
acr config
```

**Get a property:**
```bash
acr config get <property-name>
```

**Set properties:**
```bash
acr config set <property-name>=<value>
```

**Delete properties:**
```bash
acr config delete <property-name>
```

#### Configuration Properties

| Property                 | Default | Description                         |
|--------------------------|---------|-------------------------------------|
| `update.check-enabled`   | `true`  | Enable automatic update checks      |
| `update.timeout-seconds` | `60`    | Timeout for update network requests |

### Context Management

Contexts allow you to work with multiple Apicurio Registry instances. Each context stores the Registry URL and optional authentication settings.

**Create a context and connect to a registry:**
```bash
acr context create <context-name> <registry-url>

# Example:
acr context create dev http://localhost:8080
```

**Switch between contexts:**
```bash
acr context use <context-name>
```

**List all contexts:**
```bash
acr context
```

**Update a context (updates current context, or specify a context name):**
```bash
acr context update --registry-url <registry-url>
acr context update --group <group-id>
acr context update --artifact <artifact-id>
acr context update <context-name> --registry-url <registry-url>  # update specific context
```

**Delete a context:**
```bash
acr context delete <context-name>
acr context delete --all
```

Use `--no-switch-current` when creating a context to add it without switching to it.

### Authentication

The CLI supports authenticating with secured registry instances. Credentials are stored securely in the OS keychain (macOS Keychain or Linux Secret Service) — never in config files.

**Basic authentication:**
```bash
# Interactive — prompts for password
acr login --username <username>

# Non-interactive (CI/CD)
acr login --username <username> --password <password>
```

**OAuth2 client credentials:**
```bash
acr login --token-endpoint <token-endpoint-url> --client-id <client-id> --client-secret <client-secret>

# With scope
acr login --token-endpoint <token-endpoint-url> --client-id <client-id> --client-secret <client-secret> --scope <scope>
```

**Log out (clears credentials from keychain and config):**
```bash
acr logout
```

Authentication is per-context — each context can use different credentials.

**Headless/CI environments (no OS keychain):**
```bash
acr login --username <username> --password <password> --allow-unsafe-credential-storage
```
Credentials are stored in a local file instead of the OS keychain. A warning is printed. The preference is saved so subsequent commands don't need the flag again.

**Prerequisites for credential storage:**
- macOS: No prerequisites (uses Keychain)
- Linux: `secret-tool` required (`sudo apt install libsecret-tools` or `sudo dnf install libsecret`)
- Headless/CI: Use `--allow-unsafe-credential-storage` if no keychain is available

### Working with Groups

Groups organize artifacts in the registry.

**List all groups:**
```bash
acr group

# With pagination:
acr group --page <page-number> --size <page-size>

# Output as JSON:
acr group --output-type json
```

**Create a group:**
```bash
acr group create <group-id>

# With description and labels:
acr group create <group-id> --description <description> --label <key>=<value> [--label ...]
```

**Get group details:**
```bash
acr group get <group-id>

# Output as JSON:
acr group get <group-id> --output-type json
```

**Update a group:**
```bash
acr group update <group-id> --description <description>

# Set or update labels:
acr group update <group-id> --set-label <key>=<value> [--set-label ...]

# Delete labels:
acr group update <group-id> --delete-label <key> [--delete-label ...]
```

**Delete a group:**
```bash
acr group delete <group-id>

# Force delete a group that contains artifacts:
acr group delete <group-id> --force
```

> **Note:** Apicurio Registry must be configured with `apicurio.rest.deletion.group.enabled=true` to allow group deletions. By default, you cannot delete a group that contains artifacts unless you use the `--force` option.

### Working with Artifacts

Artifacts are schemas or API definitions stored in a group.

**List artifacts in a group:**
```bash
acr artifact -g <group-id>

# Uses group from context if set:
acr artifact
```

**Create an artifact:**
```bash
acr artifact create <artifact-id> -g <group-id> -f <file-path>
acr artifact create <artifact-id> -g <group-id> -f <file-path> --type <artifact-type>

# Read content from stdin:
cat schema.json | acr artifact create <artifact-id> -g <group-id> -f -
```

**Get artifact details or content:**
```bash
acr artifact get <artifact-id> -g <group-id>
acr artifact get <artifact-id> -g <group-id> --content
```

**Update artifact metadata:**
```bash
acr artifact update <artifact-id> -g <group-id> --name <name> --description <description>
```

**Delete an artifact:**
```bash
acr artifact delete <artifact-id> -g <group-id>
```

### Working with Versions

Each artifact can have multiple versions.

**List versions:**
```bash
acr artifact version -g <group-id> -a <artifact-id>
```

**Create a new version:**
```bash
acr artifact version create -g <group-id> -a <artifact-id> -f <file-path>
```

**Get version details or content:**
```bash
acr artifact version get -g <group-id> -a <artifact-id> <version>
acr artifact version get -g <group-id> -a <artifact-id> <version> --content
```

**Update version metadata or state:**
```bash
acr artifact version update -g <group-id> -a <artifact-id> <version> --description <description>
acr artifact version update -g <group-id> -a <artifact-id> <version> --state <state>
```

**Delete a version (DRAFT versions only):**
```bash
acr artifact version delete -g <group-id> -a <artifact-id> <version>
```

### Working with Comments

Add comments to artifact versions.

```bash
acr artifact version comment list -g <group-id> -a <artifact-id> -v <version>
acr artifact version comment create -g <group-id> -a <artifact-id> -v <version> -m <text>
acr artifact version comment update -g <group-id> -a <artifact-id> -v <version> <comment-id> -m <text>
acr artifact version comment delete -g <group-id> -a <artifact-id> -v <version> <comment-id>
```

### Working with Rules

Rules enforce content validation at the global, group, or artifact level.

**Global rules:**
```bash
acr rule                                                    # list global rules
acr rule create <rule-type> -c <rule-config>                 # create
acr rule get <rule-type>                                    # get
acr rule update <rule-type> -c <rule-config>                # update
acr rule delete <rule-type>                                 # delete
acr rule delete --all                                       # delete all
```

**Group rules:**
```bash
acr group rule -g <group-id>                                # list
acr group rule create -g <group-id> <rule-type> -c <rule-config>
```

**Artifact rules:**
```bash
acr artifact rule -g <group-id> -a <artifact-id>                        # list
acr artifact rule create -g <group-id> -a <artifact-id> <rule-type> -c <rule-config>
```

Valid rule types: `VALIDITY`, `COMPATIBILITY`, `INTEGRITY`

### Role Mappings

Manage role-based access control (RBAC) for the registry.

```bash
acr role                                                    # list all role mappings
acr role create <principal-id> <role> [--name <name>]       # create
acr role get <principal-id>                                 # get
acr role update <principal-id> --role <role>                 # update
acr role delete <principal-id>                              # delete
```

Valid roles: `ADMIN`, `DEVELOPER`, `READ_ONLY`

> **Note:** RBAC must be enabled on the registry (`apicurio.auth.role-based-authorization=true` with `apicurio.auth.role-source=application`).

### Search

Search across groups, artifacts, and versions.

```bash
acr search group --group <group-id>
acr search artifact --name <name> --group <group-id>
acr search version --name <name> --group <group-id> --artifact <artifact-id>
```

All filters are optional and can be combined. Additional filters include `--description`, `--type`, `--state`, `--label`, `--global-id`, `--content-id`. Pagination (`--page`, `--size`) and ordering (`--order`, `--order-by`) are supported.

### Global Options

These options work with most commands:

- `--verbose, -v` - Enable verbose output for debugging
- `--help, -h` - Show help information
- `--output-type, -o` - Set output format (table or json)

### Exit Codes

- `0` - Successful execution
- `1` - Application error
- `2` - Input validation error
- `3` - Apicurio Registry server error

## Development

### Guidelines

- Suggested reading: [Command Line Interface Guidelines](https://clig.dev)
- Use [picocli features](https://picocli.info) where possible (e.g., for parsing, validation, help generation, etc.).
- Use hierarchical *command* → *sub-command* structure to organize commands logically.
- Use `STDOUT` and `STDERR` appropriately. Use `STDERR` for logging and error messages, and `STDOUT` for command output. When outputting machine-readable formats (e.g. JSON), ensure that only the relevant data is printed to STDOUT, so that it can be easily piped to other tools.
- Default options should work for basic use cases. For example, the CLI uses `--no-switch-current` instead of `--switch-current` when creating a new context, because we expect that users would want to use the context they just added in most cases.
- Prefer long option names (e.g., `--output-type`) but add short options (e.g., `-o`) for frequently used parameters to improve usability. There is a limit to how many short options can be added.
