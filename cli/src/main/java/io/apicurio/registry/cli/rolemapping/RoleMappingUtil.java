package io.apicurio.registry.cli.rolemapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.RoleType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Columns.PRINCIPAL_ID;
import static io.apicurio.registry.cli.utils.Columns.PRINCIPAL_NAME;
import static io.apicurio.registry.cli.utils.Columns.ROLE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

public final class RoleMappingUtil {

    private static final String VALID_ROLES = Arrays.stream(RoleType.values())
            .map(RoleType::getValue)
            .collect(Collectors.joining(", "));

    private RoleMappingUtil() {
    }

    static RoleType parseRoleType(final String role) {
        return Optional.ofNullable(RoleType.forValue(role.toUpperCase(Locale.ROOT)))
                .orElseThrow(() -> new CliException(
                        "Invalid role '" + role + "'. Valid values: " + VALID_ROLES + ".",
                        VALIDATION_ERROR_RETURN_CODE));
    }

    static void printRoleMapping(final OutputBuffer output, final RoleMapping mapping,
                                 final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> out.append(MAPPER.writeValueAsString(mapping)).append('\n');
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(PRINCIPAL_ID, ROLE, PRINCIPAL_NAME);
                    table.addRow(
                            mapping.getPrincipalId(),
                            mapping.getRole() != null ? mapping.getRole().getValue() : "",
                            mapping.getPrincipalName()
                    );
                    table.print(out);
                }
            }
        });
    }

    static void printRoleMappingJson(final OutputBuffer output, final List<RoleMapping> mappings)
            throws JsonProcessingException {
        output.writeStdOutChunkWithException(out ->
                out.append(MAPPER.writeValueAsString(mappings)).append('\n'));
    }
}
