# CLI for Apicurio Registry

> NOTE: The CLI is a dev-preview project, and some features of Apicurio Registry are not supported yet. Apicurio Registry CLI is currently unstable; its arguments and behavior are subject to backwards-incompatible changes.

### Supported Platforms

The CLI is distributed as a native executable. A separate ZIP is provided for each platform:

| Platform | Architecture | ZIP Classifier | Shell |
|----------|-------------|----------------|-------|
| Linux    | x86_64      | `linux-x86_64` | bash  |
| macOS    | aarch64 (Apple Silicon) | `osx-aarch64` | zsh |

Windows is not supported.

## Installation

Prerequisites:

 - Linux (x86_64) with bash, or macOS (Apple Silicon) with zsh

To install the Apicurio Registry CLI:

1. Download the ZIP for your platform from [GitHub Releases]() or [Maven Central]().
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
acr update 3.3.0
```

**Check for updates without installing:**
```bash
acr update --check
```

**Postpone update notifications (default: 5 days):**
```bash
acr update --postpone
acr update --postpone 240  # postpone for 240 hours
```

**Install from a local ZIP:**
```bash
acr update --path /path/to/apicurio-registry-cli-3.2.5-linux-x86_64.zip
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

By default, the native build uses a Mandrel container image. To use a local GraalVM installation instead:

```bash
GRAALVM_HOME=/path/to/graalvm mvn clean package -pl cli -am -DskipTests -Dquarkus.native.container-build=false
```

When building locally (without a container), the system must have the `zlib` static library installed:

```bash
# Fedora/RHEL/CentOS
sudo dnf install zlib-static

# Debian/Ubuntu
sudo apt install zlib1g-dev
```

#### Skipping Native Build (Development Only)

During development and testing of CLI code (not the binary itself), you can skip the native
compilation to speed up the build. This produces a JVM-mode Quarkus application only —
**no installable ZIP is created**:

```bash
mvn clean package -pl cli -am -DskipTests -DcliSkipNative
```

#### GraalVM Version Compatibility

The **container-based build** (default) uses a Mandrel image and works out of the box — no GraalVM version management needed.

For **local builds** (`-Dquarkus.native.container-build=false`), GraalVM CE or Mandrel for JDK 17 or later is required. Set `GRAALVM_HOME` to point to the installation.

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
acr config get update.repo.url
```

**Set properties:**
```bash
acr config set update.check-enabled=false
acr config set update.repo.url=https://my-repo.example/maven2/io/apicurio/apicurio-registry-cli
```

**Delete properties:**
```bash
acr config delete my.custom.property
```

#### Configuration Properties

| Property | Default | Description |
|---|---|---|
| `update.check-enabled` | `true` | Enable automatic update checks |

### Context Management

Contexts allow you to work with multiple Apicurio Registry instances. A context stores the Registry URL and authentication (*TODO*) settings.

**List all contexts:**
```bash
acr context
```

**Create a new context:**
```bash
acr context create <context-name> <registry-url>

# Example:
acr context create dev https://registry.example
```

Use `--no-switch-current` to add a context without switching to it.

### Working with Groups

Groups organize artifacts in the registry.

**List all groups:**
```bash
acr group

# With pagination:
acr group --page 2 --size 50

# Output as JSON:
acr group --output-type json
```

**Create a group:**
```bash
acr group create <group-id>

# With description and labels:
acr group create my-group --description "My group" --label env=dev --label team=backend
```

**Get group details:**
```bash
acr group get <group-id>

# Output as JSON:
acr group get my-group --output-type json
```

**Update a group:**
```bash
acr group update <group-id> --description "Updated description"

# Set or update labels:
acr group update my-group --set-label env=prod --set-label owner=alice

# Delete labels:
acr group update my-group --delete-label env
```

**Delete a group:**
```bash
acr group delete <group-id>

# Force delete a group that contains artifacts:
acr group delete my-group --force
```

> **Note:** Apicurio Registry must be configured with `apicurio.rest.deletion.group.enabled=true` to allow group deletions. By default, you cannot delete a group that contains artifacts unless you use the `--force` option.

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
- Use [picocli features](https://picocli.info/) where possible (e.g., for parsing, validation, help generation, etc.).
- Use hierarchical *command* → *sub-command* structure to organize commands logically.
- Use `STDOUT` and `STDERR` appropriately. Use `STDERR` for logging and error messages, and `STDOUT` for command output. When outputting machine-readable formats (e.g. JSON), ensure that only the relevant data is printed to STDOUT, so that it can be easily piped to other tools.
- Default options should work for basic use cases. For example, the CLI uses `--no-switch-current` instead of `--switch-current` when creating a new context, because we expect that users would want to use the context they just added in most cases.
- Prefer long option names (e.g., `--output-type`) but add short options (e.g., `-o`) for frequently used parameters to improve usability. There is a limit to how many short options can be added.
