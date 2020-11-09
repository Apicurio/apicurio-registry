Registry uses [Picocli](https://picocli.info/) to implement its CLI commands.

At build time we create an uber-jar, holding all required dependencies.
This uber-jar acts as a sort of executable.

The Registry CLI can be used in two different ways:
* as a single command [1]
* as an interactive shell [2]

To ease the usage we suggest adding aliases:
* for [1] use this: `alias rscli='java -cp "<PATH_TO_REGISTRY_PROJECT>/cli/target/apicurio-registry-cli-2.0.0-SNAPSHOT.jar" io.apicurio.registry.cli.EntryCommand'`
* for [2] use this: `alias rsmain='java -jar <PATH_TO_REGISTRY_PROJECT>/cli/target/apicurio-registry-cli-2.0.0-SNAPSHOT.jar'`
* or simply start shell mode with: `java -jar <PATH_TO_REGISTRY_PROJECT>/cli/target/apicurio-registry-cli-2.0.0-SNAPSHOT.jar`

During the build you can also find completion script in target/ directory: `rscli_completion.sh`.
You can `source` this script and get tab completion ootb.

Shell mode also comes with full tab completion and suggestion support.

Once you have things up-n-running, simply ask CLI for `help`. It will print out the usage, etc ;-) 

This is a wip, not all client methods are yet supported. Fill free to contribute!
