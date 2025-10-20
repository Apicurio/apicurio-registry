package io.apicurio.registry.utils.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.export.mappers.ArtifactReferenceMapper;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.EntityWriter;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

/**
 * Export utility for Confluent Schema Registry that exports data in Apicurio Registry v3 format.
 * This version fixes bugs in the original v2 exporter:
 * - Generates unique globalIds for each version
 * - Sets versionOrder correctly
 * - Creates proper branch entities
 * - Uses v3 entity structure
 */
@QuarkusMain(name = "ConfluentExport")
public class Export implements QuarkusApplication {

    @Inject
    Logger log;

    @Inject
    ArtifactReferenceMapper artifactReferenceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main entry point for the export utility.
     *
     * @param args command line arguments
     * @return exit code
     * @throws Exception if export fails
     */
    @Override
    public int run(String... args) throws Exception {

        OptionsParser optionsParser = new OptionsParser(args);
        if (optionsParser.getUrl() == null) {
            log.error("Missing required argument, confluent schema registry url");
            log.error("Usage: export <url> [--output|-o <output-file>] [--insecure] [--client-props key1=value1 key2=value2 ...]");
            return 1;
        }

        String url = optionsParser.getUrl();
        String outputFileName = optionsParser.getOutputFile();
        Map<String, Object> conf = optionsParser.getClientProps();

        RestService restService = new RestService(url);

        if (optionsParser.isInSecure()) {
            restService.setSslSocketFactory(getInsecureSSLSocketFactory());
            restService.setHostnameVerifier(new FakeHostnameVerifier());
        }

        SchemaRegistryClient client = new CachedSchemaRegistryClient(restService, 64, conf);

        File output = new File(outputFileName);
        try (FileOutputStream fos = new FileOutputStream(output)) {

            log.info("Exporting confluent schema registry data to " + output.getName() + " (v3 format)");
            System.out.println("Exporting confluent schema registry data to " + output.getName() + " (v3 format)");

            ZipOutputStream zip = new ZipOutputStream(fos, StandardCharsets.UTF_8);
            EntityWriter writer = new EntityWriter(zip);

            // Data structures for export
            AtomicLong globalIdCounter = new AtomicLong(1);
            Map<String, Long> contentHashToContentId = new HashMap<>();
            Map<String, List<Integer>> versionsBySubject = new HashMap<>();
            Map<String, String> artifactTypeBySubject = new HashMap<>();

            // Get all subjects
            Collection<String> subjects = client.getAllSubjects();
            List<String> sortedSubjects = new ArrayList<>(subjects);
            sortedSubjects.sort(Comparator.naturalOrder());

            // =========================================================================
            // STEP 1: Collect version information for all subjects
            // =========================================================================
            log.info("Step 1: Collecting version information");
            for (String subject : sortedSubjects) {
                List<Integer> versions = client.getAllVersions(subject);
                versions.sort(Comparator.naturalOrder());
                versionsBySubject.put(subject, versions);

                // Get artifact type from first version
                if (!versions.isEmpty()) {
                    Schema schema = client.getByVersion(subject, versions.get(0), false);
                    artifactTypeBySubject.put(subject, schema.getSchemaType().toUpperCase(Locale.ROOT));
                }
            }

            // =========================================================================
            // STEP 2: Write Manifest
            // =========================================================================
            log.info("Step 2: Writing manifest");
            ManifestEntity manifest = new ManifestEntity();
            manifest.exportedBy = "export-confluent-utility-v3";
            manifest.exportedOn = new Date();
            manifest.systemDescription = "Remote Confluent Schema Registry (export created using apicurio confluent schema registry export utility v3).";
            manifest.systemName = "Remote Confluent Schema Registry";
            manifest.systemVersion = "3.x";
            manifest.exportVersion = "3.1";
            writer.writeEntity(manifest);

            // =========================================================================
            // STEP 3: Export all content
            // =========================================================================
            log.info("Step 3: Exporting content");
            int contentCount = 0;
            for (String subject : sortedSubjects) {
                List<Integer> versions = versionsBySubject.get(subject);
                for (Integer version : versions) {
                    contentCount += exportContent(restService, client, subject, version,
                                                  contentHashToContentId, writer);
                }
            }
            log.info("Exported " + contentCount + " content entities");

            // =========================================================================
            // STEP 4: Export all artifacts
            // =========================================================================
            log.info("Step 4: Exporting artifacts");
            for (String subject : sortedSubjects) {
                exportArtifact(subject, artifactTypeBySubject.get(subject), writer);
            }
            log.info("Exported " + sortedSubjects.size() + " artifacts");

            // =========================================================================
            // STEP 5: Export all artifact versions
            // =========================================================================
            log.info("Step 5: Exporting artifact versions");
            int versionCount = 0;
            for (String subject : sortedSubjects) {
                List<Integer> versions = versionsBySubject.get(subject);
                for (Integer version : versions) {
                    exportArtifactVersion(restService, client, subject, version,
                                        globalIdCounter, contentHashToContentId, writer);
                    versionCount++;
                }
            }
            log.info("Exported " + versionCount + " artifact versions");

            // =========================================================================
            // STEP 6: Export all branches
            // =========================================================================
            log.info("Step 6: Exporting branches");
            for (String subject : sortedSubjects) {
                exportBranch(subject, versionsBySubject.get(subject), writer);
            }
            log.info("Exported " + sortedSubjects.size() + " branches");

            // =========================================================================
            // STEP 7: Export artifact rules
            // =========================================================================
            log.info("Step 7: Exporting artifact rules");
            int artifactRuleCount = 0;
            for (String subject : sortedSubjects) {
                try {
                    String compatibility = client.getCompatibility(subject);
                    exportArtifactRule(subject, compatibility, writer);
                    artifactRuleCount++;
                } catch (RestClientException ex) {
                    // Subject does not have specific compatibility rule
                }
            }
            log.info("Exported " + artifactRuleCount + " artifact rules");

            // =========================================================================
            // STEP 8: Export global rules
            // =========================================================================
            log.info("Step 8: Exporting global rules");
            String globalCompatibility = client.getCompatibility(null);
            exportGlobalRule(RuleType.COMPATIBILITY, globalCompatibility, writer);
            exportGlobalRule(RuleType.VALIDITY, "SYNTAX_ONLY", writer);
            log.info("Exported 2 global rules");

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

    /**
     * Exports content for a specific subject version.
     *
     * @param restService REST service for Confluent Schema Registry
     * @param client Schema Registry client
     * @param subject subject name
     * @param version version number
     * @param contentHashToContentId map to track content by hash
     * @param writer entity writer
     * @return number of content entities exported (0 or 1)
     * @throws RestClientException if Confluent API call fails
     * @throws IOException if write fails
     */
    private int exportContent(RestService restService, SchemaRegistryClient client,
                             String subject, Integer version,
                             Map<String, Long> contentHashToContentId,
                             EntityWriter writer) throws RestClientException, IOException {

        Schema schema = client.getByVersion(subject, version, false);
        SchemaString schemaString = restService.getId(schema.getId());

        String content = schemaString.getSchemaString();
        byte[] contentBytes = IoUtil.toBytes(content);
        String contentHash = DigestUtils.sha256Hex(contentBytes);

        // Check if we've already exported this content
        if (contentHashToContentId.containsKey(contentHash)) {
            return 0; // Content already exported, skip
        }

        // Export references first
        for (SchemaReference ref : schema.getReferences()) {
            exportContent(restService, client, ref.getSubject(), ref.getVersion(),
                         contentHashToContentId, writer);
        }

        List<ArtifactReference> references = artifactReferenceMapper.map(schema.getReferences());
        String artifactType = schema.getSchemaType().toUpperCase(Locale.ROOT);

        ContentEntity contentEntity = ContentEntity.builder()
                .contentId(schema.getId())
                .contentHash(contentHash)
                .canonicalHash(null) // Will be calculated during import
                .artifactType(artifactType)
                .contentType(determineContentType(artifactType))
                .contentBytes(contentBytes)
                .serializedReferences(serializeReferences(references))
                .build();

        writer.writeEntity(contentEntity);
        contentHashToContentId.put(contentHash, (long) schema.getId());

        return 1;
    }

    /**
     * Exports an artifact entity.
     *
     * @param subject subject name (artifact ID)
     * @param artifactType artifact type
     * @param writer entity writer
     * @throws IOException if write fails
     */
    private void exportArtifact(String subject, String artifactType, EntityWriter writer)
            throws IOException {

        long now = System.currentTimeMillis();

        ArtifactEntity artifactEntity = ArtifactEntity.builder()
                .groupId(null) // Maps to "default"
                .artifactId(subject)
                .artifactType(artifactType)
                .name(null)
                .description(null)
                .labels(Collections.emptyMap())
                .owner("export-confluent-utility")
                .createdOn(now)
                .modifiedBy("export-confluent-utility")
                .modifiedOn(now)
                .build();

        writer.writeEntity(artifactEntity);
    }

    /**
     * Exports an artifact version entity.
     *
     * @param restService REST service for Confluent Schema Registry
     * @param client Schema Registry client
     * @param subject subject name
     * @param version version number
     * @param globalIdCounter counter for generating unique global IDs
     * @param contentHashToContentId map to get contentId by hash
     * @param writer entity writer
     * @throws RestClientException if Confluent API call fails
     * @throws IOException if write fails
     */
    private void exportArtifactVersion(RestService restService, SchemaRegistryClient client,
                                      String subject, Integer version,
                                      AtomicLong globalIdCounter,
                                      Map<String, Long> contentHashToContentId,
                                      EntityWriter writer) throws RestClientException, IOException {

        Schema schema = client.getByVersion(subject, version, false);
        SchemaString schemaString = restService.getId(schema.getId());

        String content = schemaString.getSchemaString();
        byte[] contentBytes = IoUtil.toBytes(content);
        String contentHash = DigestUtils.sha256Hex(contentBytes);

        Long contentId = contentHashToContentId.get(contentHash);
        long now = System.currentTimeMillis();

        ArtifactVersionEntity versionEntity = ArtifactVersionEntity.builder()
                .globalId(globalIdCounter.getAndIncrement()) // Unique sequential ID
                .groupId(null)
                .artifactId(subject)
                .version(String.valueOf(version))
                .versionOrder(version) // Matches Confluent version number
                .state(VersionState.ENABLED)
                .name(null)
                .description(null)
                .owner("export-confluent-utility")
                .labels(Collections.emptyMap())
                .createdOn(now)
                .modifiedBy("export-confluent-utility")
                .modifiedOn(now)
                .contentId(contentId)
                .build();

        writer.writeEntity(versionEntity);
    }

    /**
     * Exports the "latest" branch for a subject.
     *
     * @param subject subject name
     * @param versions list of version numbers
     * @param writer entity writer
     * @throws IOException if write fails
     */
    private void exportBranch(String subject, List<Integer> versions, EntityWriter writer)
            throws IOException {

        long now = System.currentTimeMillis();

        // Convert version numbers to strings
        List<String> versionStrings = versions.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        BranchEntity branchEntity = BranchEntity.builder()
                .groupId(null)
                .artifactId(subject)
                .branchId("latest")
                .systemDefined(true) // System-defined branch
                .versions(versionStrings)
                .description(null)
                .owner("export-confluent-utility")
                .createdOn(now)
                .modifiedBy("export-confluent-utility")
                .modifiedOn(now)
                .build();

        writer.writeEntity(branchEntity);
    }

    /**
     * Exports an artifact rule.
     *
     * @param subject subject name
     * @param compatibility compatibility level
     * @param writer entity writer
     * @throws IOException if write fails
     */
    private void exportArtifactRule(String subject, String compatibility, EntityWriter writer)
            throws IOException {

        ArtifactRuleEntity ruleEntity = ArtifactRuleEntity.builder()
                .groupId(null)
                .artifactId(subject)
                .type(RuleType.COMPATIBILITY)
                .configuration(compatibility)
                .build();

        writer.writeEntity(ruleEntity);
    }

    /**
     * Exports a global rule.
     *
     * @param ruleType rule type
     * @param configuration rule configuration
     * @param writer entity writer
     * @throws IOException if write fails
     */
    private void exportGlobalRule(RuleType ruleType, String configuration, EntityWriter writer)
            throws IOException {

        GlobalRuleEntity ruleEntity = GlobalRuleEntity.builder()
                .ruleType(ruleType)
                .configuration(configuration)
                .build();

        writer.writeEntity(ruleEntity);
    }

    /**
     * Determines the content type based on the artifact type.
     *
     * @param artifactType artifact type
     * @return content type
     */
    private String determineContentType(String artifactType) {
        switch (artifactType.toUpperCase()) {
            case "AVRO":
            case "JSON":
                return ContentTypes.APPLICATION_JSON;
            case "PROTOBUF":
                return ContentTypes.APPLICATION_PROTOBUF;
            default:
                return ContentTypes.APPLICATION_JSON; // safe default
        }
    }

    /**
     * Serializes the given collection of references to a string.
     *
     * @param references list of artifact references
     * @return serialized references as JSON string
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

    /**
     * Creates an insecure SSL socket factory for testing purposes.
     *
     * @return SSL socket factory
     */
    public SSLSocketFactory getInsecureSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new FakeTrustManager() }, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception ex) {
            log.error("Could not create Insecure SSL Socket Factory", ex);
        }
        return null;
    }

}