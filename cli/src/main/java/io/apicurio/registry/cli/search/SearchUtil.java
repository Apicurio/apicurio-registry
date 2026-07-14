package io.apicurio.registry.cli.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

final class SearchUtil {

    private SearchUtil() {
    }

    static void printArtifactResults(final OutputBuffer output, final ArtifactSearchResults results,
                                     final OutputTypeMixin outputType, final PaginationMixin pagination)
            throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(results));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(GROUP_ID, ARTIFACT_ID, NAME, ARTIFACT_TYPE, DESCRIPTION,
                            CREATED_ON, OWNER, MODIFIED_ON, MODIFIED_BY, LABELS);
                    Optional.ofNullable(results.getArtifacts()).orElse(List.of()).forEach(a -> {
                        table.addRow(
                                displayGroupId(a.getGroupId()),
                                a.getArtifactId(),
                                a.getName(),
                                a.getArtifactType(),
                                a.getDescription(),
                                convertToString(a.getCreatedOn()),
                                a.getOwner(),
                                convertToString(a.getModifiedOn()),
                                a.getModifiedBy(),
                                convertToString(a.getLabels())
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), results.getCount());
                    table.print(out);
                }
            }
        });
    }
}
