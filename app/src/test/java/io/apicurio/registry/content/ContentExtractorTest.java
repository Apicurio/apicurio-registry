/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.content;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class ContentExtractorTest extends AbstractRegistryTestBase {

    private static final String avroFormat = "{\r\n" +
                                             "     \"type\": \"record\",\r\n" +
                                             "     \"namespace\": \"com.example\",\r\n" +
                                             "     \"name\": \"%s\",\r\n" +
                                             "     \"fields\": [\r\n" +
                                             "       { \"name\": \"first\", \"type\": \"string\" },\r\n" +
                                             "       { \"name\": \"middle\", \"type\": \"string\" },\r\n" +
                                             "       { \"name\": \"last\", \"type\": \"string\" }\r\n" +
                                             "     ]\r\n" +
                                             "} ";

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Test
    public void testAvro() {
        String name = generateArtifactId();
        String content = String.format(avroFormat, name);

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(ArtifactType.AVRO);
        ContentExtractor extractor = provider.getContentExtractor();

        EditableMetaData emd = extractor.extract(ContentHandle.create(content));
        Assertions.assertTrue(extractor.isExtracted(emd));
        Assertions.assertEquals(name, emd.getName());
    }

    @RegistryServiceTest
    public void testAvro(Supplier<RegistryService> supplier) {
        String artifactId = generateArtifactId();

        String name = generateArtifactId();
        String content = String.format(avroFormat, name);

        CompletionStage<ArtifactMetaData> cs = supplier.get().createArtifact(ArtifactType.AVRO, artifactId, new ByteArrayInputStream(content.getBytes()));
        ArtifactMetaData amd = ConcurrentUtil.result(cs);
        Assertions.assertEquals(name, amd.getName());

        // test update

        name = generateArtifactId();
        content = String.format(avroFormat, name);

        cs = supplier.get().updateArtifact(artifactId, ArtifactType.AVRO, new ByteArrayInputStream(content.getBytes()));
        amd = ConcurrentUtil.result(cs);
        Assertions.assertEquals(name, amd.getName());
    }
}
