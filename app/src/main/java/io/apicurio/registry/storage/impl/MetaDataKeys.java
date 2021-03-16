/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.storage.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.ArtifactType;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ales Justin
 */
public class MetaDataKeys {
    public static String GROUP_ID = "group_id";
    public static String ARTIFACT_ID = "artifact_id";
    public static String CONTENT = "content"; // TODO discuss
    public static String CONTENT_ID = "content_id";
    public static String CONTENT_HASH = "content_hash";
    public static String CANONICAL_HASH = "canonical_hash";
    public static String GLOBAL_ID = "global_id";
    public static String VERSION = "version";
    public static String VERSION_ID = "version_id";
    public static String NAME = "name";
    public static String TYPE = "type";
    public static String DESCRIPTION = "description";
    public static String CREATED_BY = "createdBy";
    public static String CREATED_ON = "createdOn";
    public static String MODIFIED_BY = "modifiedBy";
    public static String MODIFIED_ON = "modifiedOn";
    public static String STATE = "state";
    public static String LABELS = "labels";
    public static String PROPERTIES = "properties";

    public static String EVERYTHING = "everything";

    public final static Map<String, String> SEARCH_KEY_MAPPING = new HashMap<>();

    static {
        SEARCH_KEY_MAPPING.put("group", GROUP_ID);
        SEARCH_KEY_MAPPING.put("artifactId", ARTIFACT_ID);
        SEARCH_KEY_MAPPING.put("globalId", GLOBAL_ID);
        SEARCH_KEY_MAPPING.put("everything", EVERYTHING);
        SEARCH_KEY_MAPPING.put("labels", LABELS);
        SEARCH_KEY_MAPPING.put("properties", PROPERTIES);
        SEARCH_KEY_MAPPING.put("name", NAME);
        SEARCH_KEY_MAPPING.put("description", DESCRIPTION);
        SEARCH_KEY_MAPPING.put("contentHash", CONTENT_HASH);
        SEARCH_KEY_MAPPING.put("canonicalHash", CANONICAL_HASH);
    }

    // Internal

    public static String DELETED = "_deleted";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Helpers

    @SuppressWarnings("unchecked")
    public static ArtifactMetaDataDto toArtifactMetaData(Map<String, String> content) {
        ArtifactMetaDataDto dto = new ArtifactMetaDataDto();

        dto.setId(content.get(ARTIFACT_ID));
        dto.setGroupId(content.get(GROUP_ID));

        String createdOn = content.get(CREATED_ON);
        String modifiedOn = content.get(MODIFIED_ON);

        dto.setCreatedBy(content.get(CREATED_BY));
        if (createdOn != null) {
            dto.setCreatedOn(Long.parseLong(createdOn)); // TODO discuss
        }
        dto.setModifiedBy(content.get(MODIFIED_BY));
        if (modifiedOn != null) {
            dto.setModifiedOn(Long.parseLong(modifiedOn)); // TODO discuss
        }
        dto.setDescription(content.get(DESCRIPTION));
        dto.setName(content.get(NAME));
        dto.setType(ArtifactType.fromValue(content.get(TYPE))); // TODO null check
        dto.setVersion(content.get(VERSION));
        dto.setVersionId(Integer.parseInt(content.get(VERSION_ID)));
        dto.setGlobalId(Long.parseLong(content.get(GLOBAL_ID)));
        dto.setState(ArtifactStateExt.getState(content));
        if (content.get(LABELS) != null) {
            dto.setLabels(Arrays.asList(content.get(LABELS).split(",")));
        }
        if (content.get(PROPERTIES) != null) {
            try {
                dto.setProperties(MAPPER.readValue(content.get(PROPERTIES), Map.class));
            } catch (JsonProcessingException e) {
                // If the user-defined properties cannot be parsed from a Json string to a Map<String, String>, ignore them
            }
        }
        return dto;
    }

    @SuppressWarnings("unchecked")
    public static ArtifactVersionMetaDataDto toArtifactVersionMetaData(Map<String, String> content) {
        ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();
        String createdOn = content.get(CREATED_ON);
        if (createdOn != null) {
            dto.setCreatedOn(Long.parseLong(createdOn)); // TODO discuss
        }
        dto.setCreatedBy(content.get(CREATED_BY));
        dto.setDescription(content.get(DESCRIPTION));
        dto.setName(content.get(NAME));
        dto.setType(ArtifactType.fromValue(content.get(TYPE)));
        dto.setVersion(content.get(VERSION));
        dto.setVersionId(Integer.parseInt(content.get(VERSION_ID)));
        dto.setGlobalId(Long.parseLong(content.get(GLOBAL_ID)));
        dto.setState(ArtifactStateExt.getState(content));
        if (content.get(LABELS) != null) {
            dto.setLabels(Arrays.asList(content.get(LABELS).split(",")));
        }
        if (content.get(PROPERTIES) != null) {
            try {
                dto.setProperties(MAPPER.readValue(content.get(PROPERTIES), Map.class));
            } catch (JsonProcessingException e) {
                // If the user-defined properties cannot be parsed from a Json string to a Map<String, String>, ignore them
            }
        }

        return dto;
    }

    public static byte[] getContent(Map<String, String> cMap) {
        String encoded = cMap.get(CONTENT);
        if (encoded == null) {
            return null;
        }
        return Base64.getDecoder().decode(encoded);
    }

    public static void putContent(Map<String, String> cMap, byte[] content) {
        cMap.put(CONTENT, Base64.getEncoder().encodeToString(content));
    }
}
