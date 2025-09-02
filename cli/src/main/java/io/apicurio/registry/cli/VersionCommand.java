package io.apicurio.registry.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import lombok.Builder;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
        name = "version",
        description = "Prints version information"
)
public class VersionCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(OutputBuffer output) throws JsonProcessingException {
        var version = Version.builder()
                .version(ConfigProvider.getConfig().getValue("version", String.class))
                .build();
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(Mapper.MAPPER.writeValueAsString(version));
                    out.append('\n');
                }
                case table -> {
                    out.append("CLI version: ")
                            .append(version.version)
                            .append('\n');
                }
            }
        });
    }

    @Builder
    public static class Version {

        @JsonProperty("CLI version")
        public String version;
    }
}
