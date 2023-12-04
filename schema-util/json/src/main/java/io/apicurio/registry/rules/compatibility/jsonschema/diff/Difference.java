package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;


@Builder
@Getter
@EqualsAndHashCode
@ToString
public class Difference {

    @NonNull
    private final DiffType diffType;

    @NonNull
    private final String pathOriginal;

    @NonNull
    private final String pathUpdated;

    @NonNull
    private final String subSchemaOriginal;

    @NonNull
    private final String subSchemaUpdated;
}
