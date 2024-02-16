package io.apicurio.registry.utils.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.export.mappers.ArtifactReferenceMapper;
import io.apicurio.registry.utils.impexp.*;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipOutputStream;

@QuarkusMain(name = "ConfluentExport")
public class Export implements QuarkusApplication {

    @Inject
    Logger log;

    @Inject
    ArtifactReferenceMapper artifactReferenceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @see QuarkusApplication#run(String[])
     */
    @Override
    public int run(String... args) throws Exception {

        OptionsParser optionsParser = new OptionsParser(args);
        if (optionsParser.getUrl() == null) {
            log.error("Missing required argument, confluent schema registry url");
            return 1;
        }

        String url = optionsParser.getUrl();
        Map<String, Object> conf = optionsParser.getClientProps();

        RestService restService = new RestService(url);

        if (optionsParser.isInSecure()) {
            restService.setSslSocketFactory(getInsecureSSLSocketFactory());
            restService.setHostnameVerifier(new FakeHostnameVerifier());
        }

        SchemaRegistryClient client = new CachedSchemaRegistryClient(restService, 64, conf);

        File output = new File("confluent-schema-registry-export.zip");
        try (FileOutputStream fos = new FileOutputStream(output)) {

            log.info("Exporting confluent schema registry data to " + output.getName());
            System.out.println("Exporting confluent schema registry data to " + output.getName());

            ZipOutputStream zip = new ZipOutputStream(fos, StandardCharsets.UTF_8);
            ExportContext context = new ExportContext(new EntityWriter(zip), restService, client);

            // Add a basic Manifest to the export
            ManifestEntity manifest = new ManifestEntity();
            manifest.exportedBy = "export-confluent-utility";
            manifest.exportedOn = new Date();
            manifest.systemDescription = "Unknown remote confluent schema registry (export created using apicurio confluent schema registry export utility).";
            manifest.systemName = "Remote Confluent Schema Registry";
            manifest.systemVersion = "n/a";
            context.getWriter().writeEntity(manifest);

            Collection<String> subjects = context.getSchemaRegistryClient().getAllSubjects();

            // Export all subjects
            for (String subject : subjects) {
                List<Integer> versions = context.getSchemaRegistryClient().getAllVersions(subject);
                versions.sort(Comparator.naturalOrder());

                // Export all versions of the subject
                for (Integer version : versions) {
                    exportSubjectVersionWithRefs(context, subject, version);
                }

                try {
                    String compatibility = context.getSchemaRegistryClient().getCompatibility(subject);

                    ArtifactRuleEntity ruleEntity = new ArtifactRuleEntity();
                    ruleEntity.artifactId = subject;
                    ruleEntity.configuration = compatibility;
                    ruleEntity.groupId = null;
                    ruleEntity.type = RuleType.COMPATIBILITY;

                    context.getWriter().writeEntity(ruleEntity);
                } catch (RestClientException ex) {
                    // Subject does not have specific compatibility rule
                }
            }


            String globalCompatibility = client.getCompatibility(null);

            GlobalRuleEntity ruleEntity = new GlobalRuleEntity();
            ruleEntity.configuration = globalCompatibility;
            ruleEntity.ruleType = RuleType.COMPATIBILITY;

            context.getWriter().writeEntity(ruleEntity);

            // Enable Global Validation rule bcs it is confluent default behavior
            GlobalRuleEntity ruleEntity2 = new GlobalRuleEntity();
            ruleEntity2.configuration = "SYNTAX_ONLY";
            ruleEntity2.ruleType = RuleType.VALIDITY;

            context.getWriter().writeEntity(ruleEntity2);

            zip.flush();
            zip.close();
        } catch (Exception ex) {
            log.error("Export was not successful", ex);
            return 1;
        }

        log.info("Export successfully done.");
        System.out.println("Export successfully done.");

        return 0;
    }

    public SSLSocketFactory getInsecureSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{new FakeTrustManager()}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception ex) {
            log.error("Could not create Insecure SSL Socket Factory", ex);
        }
        return null;
    }

    public void exportSubjectVersionWithRefs(ExportContext context, String subject, Integer version) throws RestClientException, IOException {
        if (context.getExportedSubjectVersions().stream().anyMatch(subjectVersionPair -> subjectVersionPair.is(subject, version))) {
            return;
        }
        context.getExportedSubjectVersions().add(new SubjectVersionPair(subject, version));

        List<Integer> versions = context.getSchemaRegistryClient().getAllVersions(subject);
        versions.sort(Comparator.reverseOrder());

        Schema metadata = context.getSchemaRegistryClient().getByVersion(subject, version, false);

        SchemaString schemaString = context.getRestService().getId(metadata.getId());

        String content = schemaString.getSchemaString();
        byte[] contentBytes = IoUtil.toBytes(content);
        String contentHash = DigestUtils.sha256Hex(contentBytes);

        // Export all references first
        for (SchemaReference ref : metadata.getReferences()) {
            exportSubjectVersionWithRefs(context, ref.getSubject(), ref.getVersion());
        }

        List<ArtifactReference> references = artifactReferenceMapper.map(metadata.getReferences());

        String artifactType = metadata.getSchemaType().toUpperCase(Locale.ROOT);

        Long contentId = context.getContentIndex().computeIfAbsent(contentHash, k -> {
            ContentEntity contentEntity = new ContentEntity();
            contentEntity.contentId = metadata.getId();
            contentEntity.contentHash = contentHash;
            contentEntity.canonicalHash = null;
            contentEntity.contentBytes = contentBytes;
            contentEntity.artifactType = artifactType;
            contentEntity.serializedReferences = serializeReferences(references);
            try {
                context.getWriter().writeEntity(contentEntity);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return contentEntity.contentId;
        });

        ArtifactVersionEntity versionEntity = new ArtifactVersionEntity();
        versionEntity.artifactId = subject;
        versionEntity.artifactType = artifactType;
        versionEntity.contentId = contentId;
        versionEntity.owner = "export-confluent-utility";
        versionEntity.createdOn = System.currentTimeMillis();
        versionEntity.description = null;
        versionEntity.globalId = -1;
        versionEntity.groupId = null;
        versionEntity.labels = null;
        versionEntity.name = null;
        versionEntity.state = ArtifactState.ENABLED;
        versionEntity.version = String.valueOf(metadata.getVersion());
        versionEntity.versionOrder = metadata.getVersion();

        context.getWriter().writeEntity(versionEntity);
    }

    /**
     * Serializes the given collection of references to a string
     * @param references
     */
    private String serializeReferences(List<ArtifactReference> references) {
        try {
            if (references == null || references.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
