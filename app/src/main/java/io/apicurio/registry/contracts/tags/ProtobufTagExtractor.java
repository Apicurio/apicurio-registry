package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import com.squareup.wire.schema.internal.parser.TypeElement;
import com.squareup.wire.schema.Location;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ProtobufTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(ProtobufTagExtractor.class);

    private static final Pattern TAG_COMMENT_PATTERN = Pattern.compile("@tag:\\s*(\\S+)");

    private static final String CONFLUENT_FIELD_META = "confluent.field_meta";

    @Override
    public String getArtifactType() {
        return ArtifactType.PROTOBUF;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        try {
            Location location = Location.get("");
            ProtoFileElement protoFile = ProtoParser.Companion.parse(location, content.content());
            Map<String, Set<String>> result = new LinkedHashMap<>();
            for (TypeElement type : protoFile.getTypes()) {
                if (type instanceof MessageElement message) {
                    extractTagsFromMessage(message, "", result);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to extract tags from Protobuf schema: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void extractTagsFromMessage(MessageElement message, String pathPrefix,
            Map<String, Set<String>> result) {
        for (FieldElement field : message.getFields()) {
            String fieldPath = pathPrefix.isEmpty() ? field.getName()
                    : pathPrefix + "." + field.getName();

            Set<String> tags = extractFieldTags(field);
            if (!tags.isEmpty()) {
                result.put(fieldPath, tags);
            }
        }

        for (OneOfElement oneOf : message.getOneOfs()) {
            for (FieldElement field : oneOf.getFields()) {
                String fieldPath = pathPrefix.isEmpty() ? field.getName()
                        : pathPrefix + "." + field.getName();

                Set<String> tags = extractFieldTags(field);
                if (!tags.isEmpty()) {
                    result.put(fieldPath, tags);
                }
            }
        }

        for (TypeElement nestedType : message.getNestedTypes()) {
            if (nestedType instanceof MessageElement nested) {
                String nestedPrefix = pathPrefix.isEmpty() ? nested.getName()
                        : pathPrefix + "." + nested.getName();
                extractTagsFromMessage(nested, nestedPrefix, result);
            }
        }
    }

    private Set<String> extractFieldTags(FieldElement field) {
        Set<String> tags = new HashSet<>();
        extractTagsFromComments(field.getDocumentation(), tags);
        extractTagsFromOptions(field, tags);
        return tags;
    }

    private void extractTagsFromComments(String documentation, Set<String> tags) {
        if (documentation == null || documentation.isEmpty()) {
            return;
        }
        Matcher matcher = TAG_COMMENT_PATTERN.matcher(documentation);
        while (matcher.find()) {
            String tagValue = matcher.group(1);
            for (String tag : tagValue.split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void extractTagsFromOptions(FieldElement field, Set<String> tags) {
        List<com.squareup.wire.schema.internal.parser.OptionElement> options = field.getOptions();
        if (options == null) {
            return;
        }
        for (com.squareup.wire.schema.internal.parser.OptionElement option : options) {
            if (CONFLUENT_FIELD_META.equals(option.getName())) {
                Object value = option.getValue();
                if (value instanceof Map) {
                    Object tagsList = ((Map<String, Object>) value).get("tags");
                    if (tagsList instanceof List) {
                        for (Object tag : (List<Object>) tagsList) {
                            if (tag != null) {
                                tags.add(tag.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}
