package io.apicurio.registry.cli.common;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class InteractiveMixin {

    public static final String OPTION_NAME = "--interactive";

    @Option(names = {OPTION_NAME}, description = "[Experimental] Launch interactive TUI mode. Subject to backwards-incompatible change or removal.")
    private boolean interactive;

    public static boolean isRequested(CommandLine.ParseResult parseResult) {
        return parseResult.hasMatchedOption(OPTION_NAME);
    }
}
