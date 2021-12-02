# Apicurio Registry CLI tool

This is a WIP, not all client methods are yet supported or some existing methods may be modified. Feel free to contribute!

This is a cli tool for Apicurio Registry, it uses [Picocli](https://picocli.info/) and Quarkus.

Picocli is the framework used to implement the cli, commands, arguments,...
Quarkus is used for packaging, allowing to easily compile the tool to a native executable.

The Registry CLI only can be used as a single command.

### Compile the tool to a jar.
```
mvn package
```
### Compile the tool to a native executable.
Use this command if you don't have GraalVM installed in your machine
```
mvn package -Pnative -Dquarkus.native.container-build=true
```
If you have GraalVM installed:
```
mvn package -Pnative
```

### To ease the usage we suggest adding aliases:
* if compiled to a jar: `alias rscli='java -jar <PATH_TO_REGISTRY_PROJECT>/cli/target/apicurio-registry-cli-*-runner.jar'`
* if compiled to a native executable: `alias rscli=<PATH_TO_REGISTRY_PROJECT>/cli/target/apicurio-registry-cli-*-runner`

During the build you can also find completion script in target/ directory: `rscli_completion.sh`.
You can `source` this script and get tab completion ootb.

Once you have things up-n-running, simply ask CLI for `help`. It will print out the usage, etc ;-) 

