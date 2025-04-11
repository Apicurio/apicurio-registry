package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.List;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

public enum JsonSchemaVersion {

    DRAFT_2020_12(compile(".*draft.2020-12.*"), "$id"),
    DRAFT_2019_09(compile(".*draft.2019-09.*"), "$id"),
    DRAFT_7(compile(".*draft.07.*"), "$id"),
    DRAFT_6(compile(".*draft.06.*"), "$id"),
    DRAFT_4(compile("(.*draft.04.*)|(https?://json-schema\\.org/schema.*)"), "id"),
    UNKNOWN(null, null);

    private static final String SCHEMA_KEYWORD = "$schema";

    private static final List<JsonSchemaVersion> VERSIONS = List.of(
            DRAFT_2020_12,
            DRAFT_2019_09,
            DRAFT_7,
            DRAFT_6,
            DRAFT_4
    );

    private final Pattern pattern;

    @Getter
    private final String idKeyword;

    JsonSchemaVersion(Pattern pattern, String idKeyword) {
        this.pattern = pattern;
        this.idKeyword = idKeyword;
    }

    public static JsonSchemaVersion detect(JsonNode data) {
        if (data.has(SCHEMA_KEYWORD)) {
            String rawSchema = data.get(SCHEMA_KEYWORD).asText();
            if (!rawSchema.isBlank()) {
                return VERSIONS.stream().filter(v -> v.pattern.matcher(rawSchema).matches()).findFirst().orElse(UNKNOWN);
            }
        }
        return UNKNOWN;
    }
}
