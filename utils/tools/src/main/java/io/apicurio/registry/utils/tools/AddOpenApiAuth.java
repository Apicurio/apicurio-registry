package io.apicurio.registry.utils.tools;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30SecurityRequirement;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30SecurityScheme;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class AddOpenApiAuth {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: AddOpenApiAuth <inputFile> <outputFile>");
            System.exit(1);
        }
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        if (!inputFile.isFile()) {
            System.out.println("File not found: " + inputFile.getAbsolutePath());
            System.exit(1);
        }

        System.out.println("Loading input file from: " + inputFile.getAbsolutePath());

        // Read the input file
        String inputDocumentString;
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            inputDocumentString = IOUtils.toString(fis, StandardCharsets.UTF_8);
        }

        System.out.println("Adding security scheme and requirement.");

        // Read the source openapi document.
        OpenApi30Document document = (OpenApi30Document) Library
                .readDocumentFromJSONString(inputDocumentString);

        // Create a security scheme for basic auth
        OpenApi30SecurityScheme securityScheme = (OpenApi30SecurityScheme) document.getComponents()
                .createSecurityScheme();
        securityScheme.setType("http");
        securityScheme.setScheme("basic");
        document.getComponents().addSecurityScheme("basicAuth", securityScheme);

        // And now *use* the basic auth security scheme.
        OpenApi30SecurityRequirement securityRequirement = (OpenApi30SecurityRequirement) document
                .createSecurityRequirement();
        securityRequirement.addItem("basicAuth", Collections.emptyList());
        document.addSecurity(securityRequirement);

        // Now write out the modified document
        String outputDocumentString = Library.writeDocumentToJSONString(document);

        System.out.println("Writing modified document to: " + outputFile.getAbsolutePath());

        // Write the output to a file
        FileUtils.writeStringToFile(outputFile, outputDocumentString, StandardCharsets.UTF_8);
    }

}
