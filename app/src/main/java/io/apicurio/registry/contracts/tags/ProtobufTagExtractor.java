package io.apicurio.registry.contracts.tags;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.TypeElement;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

@ApplicationScoped
public class ProtobufTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(ProtobufTagExtractor.class);

    private static final String APICURIO_FIELD_META = "apicurio.field_meta";
    private static final String CONFLUENT_FIELD_META = "confluent.field_meta";
    private static final String TAGS_OPTION_NAME = "tags";

    @Override
    public String getArtifactType() {
        return ArtifactType.PROTOBUF;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        if (content == null || content.content() == null || content.content().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());
            Map<String, Set<String>> tags = new HashMap<>();
            extractTagsFromTypes(protoFileElement.getTypes(), "", tags);
            return tags;
        } catch (Exception e) {
            log.debug("Failed to extract tags from Protobuf content: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void extractTagsFromTypes(List<TypeElement> types, String prefix, Map<String, Set<String>> tags) {
        if (types == null) {
            return;
        }
        for (TypeElement type : types) {
            if (type instanceof MessageElement message) {
                String messagePath = prefix.isEmpty() ? message.getName() : prefix + "." + message.getName();
                extractTagsFromFields(message.getFields(), messagePath, tags);
                extractTagsFromOneOfs(message.getOneOfs(), messagePath, tags);
                extractTagsFromTypes(message.getNestedTypes(), messagePath, tags);
            }
        }
    }

    private void extractTagsFromFields(List<FieldElement> fields, String messagePath, Map<String, Set<String>> tags) {
        if (fields == null) {
            return;
        }
        for (FieldElement field : fields) {
            Set<String> fieldTags = extractFieldTags(field);
            if (!fieldTags.isEmpty()) {
                String fieldPath = messagePath + "." + field.getName();
                tags.put(fieldPath, fieldTags);
            }
        }
    }

    private void extractTagsFromOneOfs(List<OneOfElement> oneOfs, String messagePath, Map<String, Set<String>> tags) {
        if (oneOfs == null) {
            return;
        }
        for (OneOfElement oneOf : oneOfs) {
            extractTagsFromFields(oneOf.getFields(), messagePath, tags);
        }
    }

    private Set<String> extractFieldTags(FieldElement field) {
        Set<String> result = new LinkedHashSet<>();
        for (OptionElement option : field.getOptions()) {
            collectTagsFromOption(option, result);
        }
        return result;
    }

    private void collectTagsFromOption(OptionElement option, Set<String> result) {
        String name = option.getName();
        if (APICURIO_FIELD_META.equals(name) || CONFLUENT_FIELD_META.equals(name)) {
            extractTagsFromFieldMetaOption(option, result);
        }
    }

    private void extractTagsFromFieldMetaOption(OptionElement fieldMetaOption, Set<String> result) {
        Object value = fieldMetaOption.getValue();
        if (!(value instanceof OptionElement nested) || !TAGS_OPTION_NAME.equals(nested.getName())) {
            return;
        }
        Object tagValue = nested.getValue();
        if (tagValue instanceof List<?> list) {
            for (Object item : list) {
                parseTagValues(extractTagValue(item), result);
            }
        } else {
            parseTagValues(String.valueOf(tagValue), result);
        }
    }

    private String extractTagValue(Object item) {
        if (item instanceof OptionElement itemOption) {
            return String.valueOf(itemOption.getValue());
        }
        return String.valueOf(item);
    }

    private void parseTagValues(String tagValue, Set<String> result) {
        if (tagValue == null || tagValue.isBlank()) {
            return;
        }
        String[] parts = tagValue.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
    }
}
