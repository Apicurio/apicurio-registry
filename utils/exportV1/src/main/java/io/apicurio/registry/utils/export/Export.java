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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * @author Fabian Martinez
 */
@QuarkusMain
public class Export implements QuarkusApplication {

    static {
        //Workaround, because this app depends on apicurio-registry-app we are spawning an http server. Here we are setting the http port to a less probable used port
        System.setProperty("quarkus.http.port", "9573");
    }

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    /**
     * @see io.quarkus.runtime.QuarkusApplication#run(java.lang.String[])
     */
    @Override
    public int run(String... args) throws Exception {

        if (args.length == 0) {
            System.out.println("Missing required argument, registry url");
            return 1;
        }

        String url = args[0];

        Map<String, Object> conf = new HashMap<>();

        if (args.length > 2 && args[1].equals("--client-props")) {
            String[] clientconf = Arrays.copyOfRange(args, 2, args.length);
            conf = Arrays.asList(clientconf)
                .stream()
                .map(keyvalue -> keyvalue.split("="))
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
            System.out.println("Parsed client properties " + conf);
        }

        RegistryRestClient client = RegistryRestClientFactory.create(url, conf);

        File output = new File("registry-export.zip");
        try (FileOutputStream fos = new FileOutputStream(output)) {

            System.out.println("Exporting registry data to " + output.getName());

            ZipOutputStream zip = new ZipOutputStream(fos, StandardCharsets.UTF_8);
            EntityWriter writer = new EntityWriter(zip);

            // Add a basic Manifest to the export
            ManifestEntity manifest = new ManifestEntity();
            manifest.exportedBy = "export-utility-v1";
            manifest.exportedOn = new Date();
            manifest.systemDescription = "Unknown remote registry (export created using v1 export utility).";
            manifest.systemName = "Remote Registry";
            manifest.systemVersion = "n/a";
            writer.writeEntity(manifest);

            AtomicInteger contentIdSeq = new AtomicInteger(1);
            Map<String, Long> contentIndex = new HashMap<>();

            List<String> ids = client.listArtifacts();
            for (String id : ids) {
                List<Long> versions = client.listArtifactVersions(id);

                versions.sort(Comparator.naturalOrder());

                for (int i = 0; i < versions.size(); i++) {
                    Long version = versions.get(i);
                    boolean isLatest = (versions.size() - 1) == i;

                    VersionMetaData meta = client.getArtifactVersionMetaData(id, version.intValue());

                    InputStream contentStream = client.getArtifactVersion(id, version.intValue());
                    byte[] contentBytes = IoUtil.toBytes(contentStream);
                    String contentHash = DigestUtils.sha256Hex(contentBytes);
                    ContentHandle canonicalContent = this.canonicalizeContent(meta.getType(), ContentHandle.create(contentBytes));
                    byte[] canonicalContentBytes = canonicalContent.bytes();
                    String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

                    Long contentId = contentIndex.computeIfAbsent(contentHash, k -> {
                        ContentEntity contentEntity = new ContentEntity();
                        contentEntity.contentId = contentIdSeq.getAndIncrement();
                        contentEntity.contentHash = contentHash;
                        contentEntity.canonicalHash = canonicalContentHash;
                        contentEntity.contentBytes = contentBytes;

                        try {
                            writer.writeEntity(contentEntity);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        return contentEntity.contentId;
                    });

                    ArtifactVersionEntity versionEntity = new ArtifactVersionEntity();
                    versionEntity.artifactId = meta.getId();
                    versionEntity.artifactType = meta.getType();
                    versionEntity.contentId = contentId;
                    versionEntity.createdBy = meta.getCreatedBy();
                    versionEntity.createdOn = meta.getCreatedOn();
                    versionEntity.description = meta.getDescription();
                    versionEntity.globalId = meta.getGlobalId();
                    versionEntity.groupId = null;
                    versionEntity.isLatest = isLatest;
                    versionEntity.labels = meta.getLabels();
                    versionEntity.name = meta.getName();
                    versionEntity.properties = meta.getProperties();
                    versionEntity.state = meta.getState();
                    versionEntity.version = String.valueOf(meta.getVersion());
                    versionEntity.versionId = meta.getVersion();

                    writer.writeEntity(versionEntity);
                }


                List<RuleType> artifactRules = client.listArtifactRules(id);
                for (RuleType ruleType : artifactRules) {
                    Rule rule = client.getArtifactRuleConfig(id, ruleType);

                    ArtifactRuleEntity ruleEntity = new ArtifactRuleEntity();
                    ruleEntity.artifactId = id;
                    ruleEntity.configuration = rule.getConfig();
                    ruleEntity.groupId = null;
                    ruleEntity.type = ruleType;

                    writer.writeEntity(ruleEntity);
                }

            }

            List<RuleType> globalRules =client.listGlobalRules();
            for (RuleType ruleType : globalRules) {
                Rule rule = client.getGlobalRuleConfig(ruleType);

                GlobalRuleEntity ruleEntity = new GlobalRuleEntity();
                ruleEntity.configuration = rule.getConfig();
                ruleEntity.ruleType = ruleType;

                writer.writeEntity(ruleEntity);
            }

            zip.flush();
            zip.close();
        }

        return 0;
    }

    protected ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            return canonicalContent;
        } catch (Exception e) {
            e.printStackTrace();
//            log.debug("Failed to canonicalize content of type: {}", artifactType.name());
            return content;
        }
    }

}
