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

package io.apicurio.registry.utils.tools;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import static java.lang.System.out;

/**
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <m@jsenko.net>
 */
public class TransformOpenApiForClientGen {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            out.println("Usage: TransformOpenApiForClientGen <inputFile> <outputFile>");
            System.exit(1);
        }
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        if (!inputFile.isFile()) {
            out.println("File not found: " + inputFile.getAbsolutePath());
            System.exit(1);
        }

        out.println("Loading input file from: " + inputFile.getAbsolutePath());

        // Read the input file
        String inputDocumentString;
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            inputDocumentString = IOUtils.toString(fis, StandardCharsets.UTF_8);
        }

        // Read the source openapi document.
        Oas30Document document = (Oas30Document) Library.readDocumentFromJSONString(inputDocumentString);

        attachHeaderSchema(document.paths.getItem("/groups/{groupId}/artifacts/{artifactId}").put, "/groups/{groupId}/artifacts/{artifactId} PUT");
        attachHeaderSchema(document.paths.getItem("/groups/{groupId}/artifacts").post, "/groups/{groupId}/artifacts POST");
        attachHeaderSchema(document.paths.getItem("/groups/{groupId}/artifacts/{artifactId}/versions").post, "/groups/{groupId}/artifacts/{artifactId}/versions POST");

        // Now write out the modified document
        String outputDocumentString = Library.writeDocumentToJSONString(document);

        out.println("Writing modified document to: " + outputFile.getAbsolutePath());

        // Write the output to a file
        FileUtils.writeStringToFile(outputFile, outputDocumentString, StandardCharsets.UTF_8);
    }

    private static void attachHeaderSchema(OasOperation operation, String info) {
        out.println("Adding explicit Content-Type header to " + info);
        var param = operation.createParameter();
        param.name = "Content-Type";
        param.description = "This header is explicit so clients using the OpenAPI Generator are able select the content type. Ignore otherwise.";
        var schema = (Oas30Schema) param.createSchema();
        schema.type = "string";
        param.schema = schema;
        param.in = "header";
        operation.addParameter(param);
    }
}
