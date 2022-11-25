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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import io.apicurio.registry.client.request.HeadersInterceptor;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.apache.commons.codec.digest.DigestUtils;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import static io.apicurio.registry.client.request.Config.REGISTRY_REQUEST_HEADERS_PREFIX;

/**
 * @author Fabian Martinez
 */
@QuarkusMain(name = "RegistryExport")
public class Export implements QuarkusApplication {

    @Inject
    Logger log;

    ArtifactTypeUtilProviderFactory factory = new DefaultArtifactTypeUtilProviderImpl();

    private boolean matchContentId = false;

    /**
     * @see io.quarkus.runtime.QuarkusApplication#run(java.lang.String[])
     */
    @Override
    public int run(String... args) throws Exception {

        OptionsParser optionsParser = new OptionsParser(args);
        if (optionsParser.getUrl() == null) {
            log.error("Missing required argument, registry url");
            return 1;
        }

        String url = optionsParser.getUrl();
        boolean insecure = optionsParser.isInSecure();
        matchContentId = optionsParser.isMatchContentId();
        Map<String, Object> conf = optionsParser.getClientProps();

        RegistryRestClient client = buildRegistryClient(url, conf, insecure);

        File output = new File("registry-export.zip");
        try (FileOutputStream fos = new FileOutputStream(output)) {

            log.info("Exporting registry data to " + output.getName());
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

            ContentExporter contentExporter;
            if (matchContentId) {
                contentExporter = new MatchContentIdContentExporter(writer);
            } else {
                contentExporter = new DefaultContentExporter(writer);
            }

            List<String> ids = client.listArtifacts();
            for (String id : ids) {
                List<Long> versions = client.listArtifactVersions(id);

                versions.sort(Comparator.naturalOrder());

                for (int i = 0; i < versions.size(); i++) {
                    Long version = versions.get(i);
                    boolean isLatest = (versions.size() - 1) == i;

                    VersionMetaData meta = client.getArtifactVersionMetaData(id, version.intValue());

                    byte[] contentBytes;

                    if (ArtifactState.DISABLED.equals(meta.getState())) {
                        try {
                            var temporalstate = new UpdateState();
                            temporalstate.setState(ArtifactState.ENABLED);
                            client.updateArtifactVersionState(id, version.intValue(), temporalstate);

                            InputStream contentStream = client.getArtifactVersion(id, version.intValue());
                            contentBytes = IoUtil.toBytes(contentStream);

                        } finally {
                            var disabledagain = new UpdateState();
                            disabledagain.setState(ArtifactState.DISABLED);
                            client.updateArtifactVersionState(id, version.intValue(), disabledagain);
                        }
                    } else {
                        InputStream contentStream = client.getArtifactVersion(id, version.intValue());
                        contentBytes = IoUtil.toBytes(contentStream);
                    }

                    if (contentBytes == null) {
                        log.warn("An error occurred getting the content for the artifact " + id + " version " + version);
                    }

                    String contentHash = DigestUtils.sha256Hex(contentBytes);
                    ContentHandle canonicalContent = this.canonicalizeContent(meta.getType().name(), ContentHandle.create(contentBytes));
                    byte[] canonicalContentBytes = canonicalContent.bytes();
                    String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

                    Long contentId = contentExporter.writeContent(contentHash, canonicalContentHash, contentBytes, meta);

                    ArtifactVersionEntity versionEntity = new ArtifactVersionEntity();
                    versionEntity.artifactId = meta.getId();
                    versionEntity.artifactType = meta.getType().name();
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

            List<RuleType> globalRules = client.listGlobalRules();
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

        log.info("Export successfully done.");
        System.out.println("Export successfully done.");

        return 0;
    }

    protected ContentHandle canonicalizeContent(String artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content, Collections.emptyMap());
            return canonicalContent;
        } catch (Exception e) {
            log.warn("Couldn't get canonical content", e);
            return content;
        }
    }

    private RegistryRestClient buildRegistryClient(String baseUrl, Map<String, Object> configs, boolean insecure) {
        if (!insecure) {
            return RegistryRestClientFactory.create(baseUrl, configs);
        } else {
            return RegistryRestClientFactory.create(baseUrl, buildSSLInsecureHttpClient(baseUrl, configs));
        }
    }

    private OkHttpClient buildSSLInsecureHttpClient(String baseUrl, Map<String, Object> configs) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        okHttpClientBuilder = addHeaders(okHttpClientBuilder, baseUrl, configs);

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            X509TrustManager[] trustManagers = new X509TrustManager[]{new FakeTrustManager()};
            sc.init(null, trustManagers, new java.security.SecureRandom());
            okHttpClientBuilder.sslSocketFactory(sc.getSocketFactory(), trustManagers[0]);
            okHttpClientBuilder.hostnameVerifier(new FakeHostnameVerifier());
        } catch (GeneralSecurityException e) {
            // Ignore
        }

        return okHttpClientBuilder.build();
    }

    // The following method is a copy of a private method from apicurio-registry-rest-client 1.3.2-Final
    private static OkHttpClient.Builder addHeaders(OkHttpClient.Builder okHttpClientBuilder, String baseUrl, Map<String, Object> configs) {
        Map<String, String> requestHeaders = configs.entrySet().stream()
                .filter(map -> map.getKey().startsWith(REGISTRY_REQUEST_HEADERS_PREFIX))
                .collect(Collectors.toMap(map -> map.getKey()
                        .replace(REGISTRY_REQUEST_HEADERS_PREFIX, ""), map -> map.getValue().toString()));

        if (!requestHeaders.containsKey("Authorization")) {
            // Check if url includes user/password
            // and add auth header if it does
            HttpUrl url = HttpUrl.parse(baseUrl);
            String user = url.encodedUsername();
            String pwd = url.encodedPassword();
            if (user != null && !user.isEmpty()) {
                String credentials = Credentials.basic(user, pwd);
                requestHeaders.put("Authorization", credentials);
            }
        }

        if (!requestHeaders.isEmpty()) {
            final Interceptor headersInterceptor = new HeadersInterceptor(requestHeaders);
            return okHttpClientBuilder.addInterceptor(headersInterceptor);
        } else {
            return okHttpClientBuilder;
        }
    }

}
