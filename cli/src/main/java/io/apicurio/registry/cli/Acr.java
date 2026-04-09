package io.apicurio.registry.cli;

import io.apicurio.registry.cli.artifact.ArtifactCommand;
import io.apicurio.registry.cli.context.ContextCommand;
import io.apicurio.registry.cli.group.GroupCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.Getter;
import picocli.CommandLine.Command;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.ScopeType.INHERIT;

@TopCommand
@Command(
        name = "acr",
        description = "Apicurio Registry CLI",
        header = {
                "   ___        _              _",
                "  / _ | ___  (_)_____ ______(_)__",
                " / __ |/ _ \\/ / __/ // / __/ / _ \\",
                "/_/ |_/ .__/_/\\__/\\_,_/_/ /_/\\___/",
                "     /_/"
        },
        subcommands = {
                ArtifactCommand.class,
                ContextCommand.class,
                GroupCommand.class,
                InstallCommand.class,
                UpdateCommand.class,
                VersionCommand.class
        },
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                "0: Successful execution.",
                "1: Application error.",
                "2: Input validation error.",
                "3: Apicurio Registry server error."
        }
)
public class Acr {

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output.",
            scope = INHERIT
    )
    @Getter
    private boolean verbose;

    @Option(
            names = {"-h", "--help"},
            description = "Print a help message and exit.",
            usageHelp = true,
            scope = INHERIT
    )
    private boolean _ignored;
}
