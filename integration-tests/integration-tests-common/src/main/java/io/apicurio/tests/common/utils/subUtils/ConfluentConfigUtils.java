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

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.utils.BaseHttpUtils;
import io.restassured.response.Response;

/**
 * @author Fabian Martinez
 */
public class ConfluentConfigUtils {

    public static Response testCompatibility(String body, String schemaName, int returnCode) {
        try {
            URL url = new URL(TestUtils.getRegistryApiUrl() + "/ccompat/v6/compatibility/subjects/" + schemaName + "/versions/latest");
            return BaseHttpUtils.rulesPostRequest(RestConstants.SR, body, url, returnCode);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Response createGlobalCompatibilityConfig(String typeOfCompatibility) {
        try {
            URL url = new URL(TestUtils.getRegistryApiUrl() + "/ccompat/v6/config");
            return BaseHttpUtils.putRequest(RestConstants.SR, "{\"compatibility\":\"" + typeOfCompatibility + "\"}", url, 200);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Response getGlobalCompatibilityConfig() {
        try {
            URL url = new URL(TestUtils.getRegistryApiUrl() + "/ccompat/v6/config");
            return BaseHttpUtils.getRequest(RestConstants.JSON, url, 204);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

}
