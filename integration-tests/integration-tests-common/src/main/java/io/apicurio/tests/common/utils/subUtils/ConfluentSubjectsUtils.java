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

package io.apicurio.tests.common.utils.subUtils;

import static io.apicurio.tests.common.utils.BaseHttpUtils.putRequest;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.utils.BaseHttpUtils;
import io.restassured.response.Response;

/**
 * @author Fabian Martinez
 */
public class ConfluentSubjectsUtils {

    public static Response getAllSchemas(int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "", getCCompatURL("/ccompat/v6/subjects"), returnCode);
    }

    public static Response getLatestVersionSchema(String nameOfSchema) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "", getCCompatURL("/ccompat/v6/subjects/" + nameOfSchema + "/versions/latest"), 200);
    }

    public static Response createSchema(String schemeDefinition, String schemaName, int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, schemeDefinition, getCCompatURL("/ccompat/v6/subjects/" + schemaName + "/versions"), returnCode);
    }

    public static Response updateSchemaMetadata(String schemaName, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, getCCompatURL("/ccompat/v6/subjects/" + schemaName + "/meta"), returnCode);
    }

    private static URL getCCompatURL(String ccompatPath) {
        try {
            return new URL(TestUtils.getRegistryApiUrl() + ccompatPath);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

}
