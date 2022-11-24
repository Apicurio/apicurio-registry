/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.content;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.Collections;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@QuarkusTest
public class ContentCanonicalizerTest extends AbstractRegistryTestBase {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private ContentCanonicalizer getContentCanonicalizer(String type) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(type);
        return provider.getContentCanonicalizer();
    }

    @Test
    void testOpenAPI() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.OPENAPI);
        
        String before = "{\r\n" + 
                "    \"openapi\": \"3.0.2\",\r\n" + 
                "    \"info\": {\r\n" + 
                "        \"title\": \"Empty 3.0 API\",\r\n" + 
                "        \"version\": \"1.0.0\"\r\n" + 
                "    },\r\n" + 
                "    \"paths\": {\r\n" + 
                "        \"/\": {}\r\n" + 
                "    },\r\n" + 
                "    \"components\": {}\r\n" + 
                "}";
        String expected = "{\"components\":{},\"info\":{\"title\":\"Empty 3.0 API\",\"version\":\"1.0.0\"},\"openapi\":\"3.0.2\",\"paths\":{\"/\":{}}}";
        
        ContentHandle content = ContentHandle.create(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testAvro() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.AVRO);
        
        String before = "{\r\n" + 
                "     \"type\": \"record\",\r\n" + 
                "     \"namespace\": \"com.example\",\r\n" + 
                "     \"name\": \"FullName\",\r\n" + 
                "     \"fields\": [\r\n" + 
                "       { \"name\": \"first\", \"type\": \"string\" },\r\n" + 
                "       { \"name\": \"middle\", \"type\": \"string\" },\r\n" + 
                "       { \"name\": \"last\", \"type\": \"string\" }\r\n" + 
                "     ]\r\n" + 
                "} ";
        String expected = "{\"fields\":[{\"name\":\"first\",\"type\":\"string\"},{\"name\":\"last\",\"type\":\"string\"},{\"name\":\"middle\",\"type\":\"string\"}],\"name\":\"FullName\",\"namespace\":\"com.example\",\"type\":\"record\"}";
        
        ContentHandle content = ContentHandle.create(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testProtobuf() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.PROTOBUF);

        String before = "message SearchRequest {\r\n" +
                "  required string query = 1;\r\n" +
                "  optional int32 page_number = 2;\r\n" +
                "  optional int32 result_per_page = 3;\r\n" +
                "}";
        String expected = "// Proto schema formatted by Wire, do not edit.\n"
                + "// Source: \n"
                + "\n"
                + "message SearchRequest {\n"
                + "  required string query = 1;\n"
                + "\n"
                + "  optional int32 page_number = 2;\n"
                + "\n"
                + "  optional int32 result_per_page = 3;\n"
                + "}\n";

        ContentHandle content = ContentHandle.create(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGraphQL() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.GRAPHQL);
        
        String before = "type Query {\r\n" + 
                "  bookById(id: ID): Book \r\n" + 
                "}\r\n" + 
                "\r\n" + 
                "type Book {\r\n" + 
                "    id: ID\r\n" + 
                "  name: String\r\n" + 
                "   pageCount: Int\r\n" + 
                "  author: Author\r\n" + 
                "}\r\n" + 
                "\r\n" + 
                "type Author {\r\n" + 
                "  id: ID\r\n\r\n" + 
                "    firstName: String\r\n" + 
                "  lastName: String\r\n" + 
                "}\r\n\r\n";
        String expected = "type Author {\n" + 
                "  firstName: String\n" + 
                "  id: ID\n" + 
                "  lastName: String\n" + 
                "}\n" + 
                "\n" + 
                "type Book {\n" + 
                "  author: Author\n" + 
                "  id: ID\n" + 
                "  name: String\n" + 
                "  pageCount: Int\n" + 
                "}\n" + 
                "\n" + 
                "type Query {\n" + 
                "  bookById(id: ID): Book\n" + 
                "}\n" + 
                "";
        
        ContentHandle content = ContentHandle.create(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
    }
    
    @Test
    void testKafkaConnect() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.KCONNECT);
        
        String before = "{\r\n" + 
                "    \"type\": \"struct\",\r\n" + 
                "    \"fields\": [\r\n" + 
                "        {\r\n" + 
                "            \"type\": \"string\",\r\n" + 
                "            \"optional\": false,\r\n" + 
                "            \"field\": \"bar\"\r\n" + 
                "        }\r\n" + 
                "    ],\r\n" + 
                "    \"optional\": false\r\n" + 
                "}";
        String expected = "{\"fields\":[{\"field\":\"bar\",\"optional\":false,\"type\":\"string\"}],\"optional\":false,\"type\":\"struct\"}";
        
        ContentHandle content = ContentHandle.create(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
    }

    /**
    * Test method for {@link io.apicurio.registry.content.ContentCanonicalizerFactory#create(io.apicurio.registry.types.ArtifactType)}.
    */
    @Test
    void testXsd() {
       ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.XSD);

       ContentHandle content = resourceToContentHandle("xml-schema-before.xsd");
       String expected = resourceToString("xml-schema-expected.xsd");
       
       String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
       Assertions.assertEquals(expected, actual);
    }
    
    /**
     * Test method for {@link io.apicurio.registry.content.ContentCanonicalizerFactory#create(io.apicurio.registry.types.ArtifactType)}.
     */
     @Test
     void testWsdl() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.WSDL);

        ContentHandle content = resourceToContentHandle("wsdl-before.wsdl");
        String expected = resourceToString("wsdl-expected.wsdl");
        
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
        Assertions.assertEquals(expected, actual);
     }
     
     /**
      * Test method for {@link io.apicurio.registry.content.ContentCanonicalizerFactory#create(io.apicurio.registry.types.ArtifactType)}.
      */
      @Test
      void testXml() {
         ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.XML);
        
         ContentHandle content = resourceToContentHandle("xml-before.xml");
         String expected = resourceToString("xml-expected.xml");
         
         String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).content();
         Assertions.assertEquals(expected, actual);
      }
}
