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
package io.apicurio.registry.types.bigquery.provider;

import java.util.ArrayList;
import java.util.List;

import io.apicurio.registry.types.provider.*;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;

@Alternative
@Priority(Integer.MAX_VALUE)
@ApplicationScoped
public class ArtifactTypeUtilProviderImpl extends DefaultArtifactTypeUtilProviderImpl {

    @Inject
    Logger log;

    @PostConstruct
    void onConstruct() {
        log.warn("BigQuery support enabled.");
    }

    ArtifactTypeUtilProviderImpl() {
        this.providers = new ArrayList<ArtifactTypeUtilProvider>(
            List.of(
                    new AsyncApiArtifactTypeUtilProvider(),
                    new AvroArtifactTypeUtilProvider(),
                    new BigQueryArtifactTypeUtilProvider(),
                    new GraphQLArtifactTypeUtilProvider(),
                    new JsonArtifactTypeUtilProvider(),
                    new KConnectArtifactTypeUtilProvider(),
                    new OpenApiArtifactTypeUtilProvider(),
                    new ProtobufArtifactTypeUtilProvider(),
                    new WsdlArtifactTypeUtilProvider(),
                    new XmlArtifactTypeUtilProvider(),
                    new XsdArtifactTypeUtilProvider())
        );
    }

}
