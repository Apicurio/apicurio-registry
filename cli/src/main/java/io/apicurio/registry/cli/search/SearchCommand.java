package io.apicurio.registry.cli.search;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

@Command(
        name = "search",
        description = "Search for groups, artifacts, versions, and content",
        subcommands = {
                SearchArtifactsCommand.class,
                SearchByContentCommand.class,
                SearchGroupsCommand.class,
                SearchVersionsCommand.class
        }
)
public class SearchCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
