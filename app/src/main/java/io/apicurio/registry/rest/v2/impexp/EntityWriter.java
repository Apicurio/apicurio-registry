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

package io.apicurio.registry.rest.v2.impexp;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.storage.impexp.ArtifactRuleEntity;
import io.apicurio.registry.storage.impexp.ArtifactVersionEntity;
import io.apicurio.registry.storage.impexp.ContentEntity;
import io.apicurio.registry.storage.impexp.Entity;
import io.apicurio.registry.storage.impexp.EntityType;
import io.apicurio.registry.storage.impexp.GlobalRuleEntity;
import io.apicurio.registry.storage.impexp.GroupEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class EntityWriter {

    private static final Logger log = LoggerFactory.getLogger(EntityWriter.class);

    private static final ObjectMapper mapper;
    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
    }

    private final transient ZipOutputStream zip;

    /**
     * Constructor.
     * @param zip
     */
    public EntityWriter(ZipOutputStream zip) {
        this.zip = zip;
    }

    /**
     * Writes the given entity to the zip output stream.
     * @param entityType
     * @param entity
     * @throws IOException
     */
    public void writeEntity(EntityType entityType, Entity entity) throws IOException {
        switch (entityType) {
            case Content:
                writeEntity((ContentEntity) entity);
                break;
            case Group:
                writeEntity((GroupEntity) entity);
                break;
            case ArtifactVersion:
                writeEntity((ArtifactVersionEntity) entity);
                break;
            case ArtifactRule:
                writeEntity((ArtifactRuleEntity) entity);
                break;
            case GlobalRule:
                writeEntity((GlobalRuleEntity) entity);
                break;
            default:
                throw new RuntimeException("Unhandled entity type: " + entityType.name());
        }
    }

    private void writeEntity(ContentEntity entity) throws IOException {
        log.info("Exporting a content entity.");
        ZipEntry mdEntry = new ZipEntry("/content/" + entity.contentHash + ".md.json");
        ZipEntry dataEntry = new ZipEntry("/content/" + entity.contentHash + ".data");

        // Write the meta-data file.
        zip.putNextEntry(mdEntry);
        mapper.writerFor(ContentEntity.class).writeValue(zip, entity);
        zip.closeEntry();

        // Write the content file.
        zip.putNextEntry(dataEntry);
        zip.write(entity.contentBytes);
        zip.closeEntry();
    }

    private void writeEntity(GroupEntity entity) throws IOException {
        log.info("Exporting a group entity.");
        ZipEntry mdEntry = new ZipEntry(asZipPath(entity.groupId) + ".json");
        zip.putNextEntry(mdEntry);
        mapper.writerFor(GlobalRuleEntity.class).writeValue(zip, entity);
        zip.closeEntry();
    }

    private void writeEntity(ArtifactVersionEntity entity) throws IOException {
        log.info("Exporting an artifact version entity.");
        ZipEntry mdEntry = new ZipEntry(asZipPath(entity.groupId, entity.artifactId) + "/versions/" + entity.version + ".md.json");
        zip.putNextEntry(mdEntry);
        mapper.writerFor(ArtifactVersionEntity.class).writeValue(zip, entity);
        zip.closeEntry();
    }

    private void writeEntity(ArtifactRuleEntity entity) throws IOException {
        log.info("Exporting an artifact rule entity.");
        ZipEntry mdEntry = new ZipEntry(asZipPath(entity.groupId, entity.artifactId) + "/rules/" + entity.type.name() + ".json");
        zip.putNextEntry(mdEntry);
        mapper.writerFor(GlobalRuleEntity.class).writeValue(zip, entity);
        zip.closeEntry();
    }

    private void writeEntity(GlobalRuleEntity entity) throws IOException {
        log.info("Exporting a global rule entity.");
        ZipEntry mdEntry = new ZipEntry("/rules/" + entity.type.name() + ".json");
        zip.putNextEntry(mdEntry);
        mapper.writerFor(GlobalRuleEntity.class).writeValue(zip, entity);
        zip.closeEntry();
    }

    private String asZipPath(String groupId) {
        return "/groups/" + groupId;
    }

    private String asZipPath(String groupId, String artifactId) {
        return "/groups/" + groupId + "/artifacts/" + artifactId;
    }

}
