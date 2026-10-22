package io.apicurio.registry.contracts.tags;

public record FieldTag(
        String fieldPath,
        String tagName,
        TagSource source  // INLINE or EXTERNAL
) {}
