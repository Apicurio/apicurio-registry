package io.apicurio.registry.cli.common;

import lombok.Getter;
import picocli.CommandLine.Option;

public class OutputTypeMixin {

    @Option(
            names = {"-o", "--output-type"},
            description = "Write the output in the given format. Valid values: ${COMPLETION-CANDIDATES}. Default is 'table'.",
            defaultValue = "table"
    )
    @Getter
    private OutputType outputType;
}
