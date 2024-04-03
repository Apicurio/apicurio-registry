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

package io.apicurio.registry.examples.simple.json;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.kiota.http.vertx.VertXRequestAdapter;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author eric.wittmann@gmail.com
 */
public class MessageValidator {

    private final String group;
    private final String artifactId;
    private final RegistryClient client;

    /**
     * Constructor.
     *
     * @param registryUrl
     * @param group
     * @param artifactId
     */
    public MessageValidator(String registryUrl, String group, String artifactId) {
        this.group = group;
        this.artifactId = artifactId;

        VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        vertXRequestAdapter.setBaseUrl(registryUrl);
        this.client = new RegistryClient(vertXRequestAdapter);
    }

    /**
     * @param message
     */
    public void validate(MessageBean message) throws IOException, ValidationException {
        JSONObject jsonSchema;
        try (InputStream schemaIS = client.groups().byGroupId(group).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get()) {
            jsonSchema = new JSONObject(new JSONTokener(schemaIS));
        }

        JSONObject jsonSubject = new JSONObject(message);

        Schema schema = SchemaLoader.load(jsonSchema);
        schema.validate(jsonSubject);
    }

}
