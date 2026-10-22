package io.apicurio.registry.cli.serverconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;

import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.LABEL;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

final class ServerConfigUtil {

    private ServerConfigUtil() {
    }

    static void printProperty(final OutputBuffer output, final ConfigurationProperty prop,
                              final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(prop));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(NAME, prop.getName());
                    table.addRow(VALUE, prop.getValue());
                    table.addRow(LABEL, prop.getLabel());
                    table.addRow(DESCRIPTION, prop.getDescription());
                    table.print(out);
                }
            }
        });
    }
}
