package io.apicurio.registry.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.operator.OperatorException;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.json.JSONObject;

import java.util.HashMap;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class Mapper {

    private static final ObjectMapper MAPPER;

    public static final ObjectMapper YAML_MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);

        YAML_MAPPER = new YAMLMapper();
        YAML_MAPPER.registerModule(new JavaTimeModule());
        YAML_MAPPER.registerModule(new JsonOrgModule());
        YAML_MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public static String toYAML(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        try {
            var serialized = MAPPER.writeValueAsString(original);
            return MAPPER.readValue(serialized, (Class<T>) original.getClass());
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Similar to {@link Mapper#copy(Object)}, but intended specifically for Kubernetes resources.
     * After copying, the copy is sanitized from data that would interfere with using the copy as a "new" resource
     * in a create operation.
     */
    @SuppressWarnings("unchecked")
    public static <T extends HasMetadata> T copyAsNew(T original) {
        try {
            var serialized = MAPPER.writeValueAsString(original);
            var raw = MAPPER.readValue(serialized, JSONObject.class);
            raw.remove("status");
            serialized = MAPPER.writeValueAsString(raw);
            var r = MAPPER.readValue(serialized, (Class<T>) original.getClass());
            r.getMetadata().setAnnotations(new HashMap<>());
            r.getMetadata().setGeneration(null);
            r.getMetadata().setResourceVersion(null);
            return r;
        } catch (JsonProcessingException e) {
            throw new OperatorException(e);
        }
    }
}
