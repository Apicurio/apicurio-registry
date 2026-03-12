package io.apicurio.registry.cli.common;

import lombok.Getter;
import picocli.CommandLine.Option;

public class PaginationMixin {

    @Option(
            names = {"-p", "--page"},
            description = "Page number, starting at 1.",
            defaultValue = "1",
            parameterConsumer = PositiveIntegerValidator.class
    )
    @Getter
    private int page;

    @Option(
            names = {"-s", "--size"},
            description = "Number of items in the page. Default is 10.",
            defaultValue = "10",
            parameterConsumer = PositiveIntegerValidator.class
    )
    @Getter
    private int size;
}
