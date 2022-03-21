/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class ContentMapper implements RowMapper<ContentWrapperDto> {

    public static final ContentMapper instance = new ContentMapper();

    /**
     * Constructor.
     */
    private ContentMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ContentWrapperDto map(ResultSet rs) throws SQLException {
        final ContentWrapperDto contentWrapperDto = new ContentWrapperDto();
        byte[] contentBytes = rs.getBytes("content");
        ContentHandle content = ContentHandle.create(contentBytes);
        contentWrapperDto.setContent(content);
        contentWrapperDto.setReferences(SqlUtil.deserializeReferences(rs.getString("artifactreferences")));
        return contentWrapperDto;
    }

}