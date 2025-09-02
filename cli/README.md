# CLI for Apicurio Registry

> NOTE: The CLI is a dev-preview project, and some features of Apicurio Registry are not supported yet. The CLI also does not support Windows and requires bash shell.

## Installation:

Prerequisites:

 - Bash
 - Java 11 or higher

To install the Apicurio Registry CLI:

1. Download the zip file from the [GitHub Releases]() page or the [Maven Central repository]().
2. Unzip the downloaded file to a location of your choice.
3. You can run the CLI directly using `./acr`, or install it for the local user first (recommended):

   1. Run `./acr install` to install the CLI. This will install the CLI files to default locations (`$HOME/bin` and `$HOME/.apicurio/apicurio-registry-cli`), update the `~/.bashrc` file, and configure bash completions. Global installation is not supported yet.

[//]: # (### Update)

[//]: # ()
[//]: # (To update the Apicurio Registry CLI to the latest version, run `acr update`, or remove the existing installation and re-install using the steps above.)

## Build

Run `mvn clean install -pl cli -am` to build the CLI locally. The built zip file will be located in `cli/target` directory.

If you have not already installed the CLI, run:

```
unzip cli/target/apicurio-registry-cli-*.zip -d cli/target/cli && (pushd cli/target/cli && ./acr install ; popd) && source ~/.bashrc  
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
