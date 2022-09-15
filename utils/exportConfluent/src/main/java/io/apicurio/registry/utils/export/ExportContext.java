package io.apicurio.registry.utils.export;

import io.apicurio.registry.utils.impexp.EntityWriter;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportContext {
    private final EntityWriter writer;
    private final RestService restService;
    private final SchemaRegistryClient schemaRegistryClient;
    private final List<SubjectVersionPair> exportedSubjectVersions = new ArrayList<>();
    private final Map<String, Long> contentIndex = new HashMap<>();

    public ExportContext(EntityWriter writer, RestService restService, SchemaRegistryClient schemaRegistryClient) {
        this.writer = writer;
        this.restService = restService;
        this.schemaRegistryClient = schemaRegistryClient;
    }

    public EntityWriter getWriter() {
        return writer;
    }

    public SchemaRegistryClient getSchemaRegistryClient() {
        return schemaRegistryClient;
    }

    public List<SubjectVersionPair> getExportedSubjectVersions() {
        return exportedSubjectVersions;
    }

    public RestService getRestService() {
        return restService;
    }

    public Map<String, Long> getContentIndex() {
        return contentIndex;
    }
}
