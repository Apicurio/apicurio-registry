package io.apicurio.registry.cli;

import io.apicurio.registry.cli.artifact.ArtifactCommand;
import io.apicurio.registry.cli.config.ConfigPropertyCommand;
import io.apicurio.registry.cli.context.ContextCommand;
import io.apicurio.registry.cli.group.GroupCommand;
import io.apicurio.registry.cli.globalrule.GlobalRuleCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.Getter;
import picocli.CommandLine.Command;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.ScopeType.INHERIT;

@TopCommand
@Command(
        name = "acr",
        description = "{{product-name}} — manage schemas and APIs from the command line. Currently unstable; its arguments and behavior are subject to backwards-incompatible changes.",
        header = {
                "   ___        _              _",
                "  / _ | ___  (_)_____ ______(_)__",
                " / __ |/ _ \\/ / __/ // / __/ / _ \\",
                "/_/ |_/ .__/_/\\__/\\_,_/_/ /_/\\___/",
                "     /_/"
        },
        subcommands = {
                ArtifactCommand.class,
                ConfigPropertyCommand.class,
                ContextCommand.class,
                GlobalRuleCommand.class,
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
                "3: {{product-name}} server error."
        }
)
public class Acr {

    @Option(
            names = {"--verbose"},
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
