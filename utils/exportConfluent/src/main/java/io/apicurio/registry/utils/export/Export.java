/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;
import java.util.zip.ZipOutputStream;

import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.commons.codec.digest.DigestUtils;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * @author Fabian Martinez
 * @author Miroslav Safar
 */
@QuarkusMain(name = "ConfluentExport")
public class Export implements QuarkusApplication {

    @Inject
    Logger log;

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
            EntityWriter writer = new EntityWriter(zip);

            // Add a basic Manifest to the export
            ManifestEntity manifest = new ManifestEntity();
            manifest.exportedBy = "export-confluent-utility";
            manifest.exportedOn = new Date();
            manifest.systemDescription = "Unknown remote confluent schema registry (export created using apicurio confluent schema registry export utility).";
            manifest.systemName = "Remote Confluent Schema Registry";
            manifest.systemVersion = "n/a";
            writer.writeEntity(manifest);

            Map<String, Long> contentIndex = new HashMap<>();

            Collection<String> subjects = restService.getAllSubjects();
            for (String subject : subjects) {
                List<Integer> versions = restService.getAllVersions(subject);

                versions.sort(Comparator.naturalOrder());

                for (int i = 0; i < versions.size(); i++) {
                    Integer version = versions.get(i);
                    boolean isLatest = (versions.size() - 1) == i;

                    Schema metadata = restService.getVersion(subject, version);

                    SchemaString schemaString = restService.getId(metadata.getId());

                    String content = schemaString.getSchemaString();
                    byte[] contentBytes = IoUtil.toBytes(content);
                    String contentHash = DigestUtils.sha256Hex(contentBytes);

                    ArtifactType artifactType = ArtifactType.fromValue(metadata.getSchemaType().toUpperCase(Locale.ROOT));

                    Long contentId = contentIndex.computeIfAbsent(contentHash, k -> {
                        ContentEntity contentEntity = new ContentEntity();
                        contentEntity.contentId = metadata.getId();
                        contentEntity.contentHash = contentHash;
                        contentEntity.canonicalHash = null;
                        contentEntity.contentBytes = contentBytes;
                        contentEntity.artifactType = artifactType;

                        try {
                            writer.writeEntity(contentEntity);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        return contentEntity.contentId;
                    });

                    ArtifactVersionEntity versionEntity = new ArtifactVersionEntity();
                    versionEntity.artifactId = subject;
                    versionEntity.artifactType = artifactType;
                    versionEntity.contentId = contentId;
                    versionEntity.createdBy = "export-confluent-utility";
                    versionEntity.createdOn = System.currentTimeMillis();
                    versionEntity.description = null;
                    versionEntity.globalId = -1;
                    versionEntity.groupId = null;
                    versionEntity.isLatest = isLatest;
                    versionEntity.labels = null;
                    versionEntity.name = null;
                    versionEntity.properties = null;
                    versionEntity.state = ArtifactState.ENABLED;
                    versionEntity.version = String.valueOf(metadata.getVersion());
                    versionEntity.versionId = metadata.getVersion();

                    writer.writeEntity(versionEntity);
                }

                try {
                    String compatibility = client.getCompatibility(subject);

                    ArtifactRuleEntity ruleEntity = new ArtifactRuleEntity();
                    ruleEntity.artifactId = subject;
                    ruleEntity.configuration = compatibility;
                    ruleEntity.groupId = null;
                    ruleEntity.type = RuleType.COMPATIBILITY;

                    writer.writeEntity(ruleEntity);
                } catch (RestClientException ex) {
                    //Subject does not have specific compatibility rule
                }
            }

            String globalCompatibility = client.getCompatibility(null);

            GlobalRuleEntity ruleEntity = new GlobalRuleEntity();
            ruleEntity.configuration = globalCompatibility;
            ruleEntity.ruleType = RuleType.COMPATIBILITY;

            writer.writeEntity(ruleEntity);

            //Enable Global Validation rule bcs it is confluent default behavior
            GlobalRuleEntity ruleEntity2 = new GlobalRuleEntity();
            ruleEntity2.configuration = "SYNTAX_ONLY";
            ruleEntity2.ruleType = RuleType.VALIDITY;

            writer.writeEntity(ruleEntity2);

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

}
