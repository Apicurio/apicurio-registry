package io.apicurio.registry.utils.tools;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Extensible;
import io.apicurio.datamodels.models.openapi.OpenApiOperation;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Schema;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static java.lang.System.out;

/**
 * 
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
        OpenApi30Document document = (OpenApi30Document) Library
                .readDocumentFromJSONString(inputDocumentString);

        attachHeaderSchema(document.getPaths().getItem("/groups/{groupId}/artifacts/{artifactId}").getPut(),
                "/groups/{groupId}/artifacts/{artifactId} PUT");
        attachHeaderSchema(document.getPaths().getItem("/groups/{groupId}/artifacts").getPost(),
                "/groups/{groupId}/artifacts POST");
        attachHeaderSchema(
                document.getPaths().getItem("/groups/{groupId}/artifacts/{artifactId}/versions").getPost(),
                "/groups/{groupId}/artifacts/{artifactId}/versions POST");

        // Remove duplicated tags
        document.getPaths().getItem("/admin/artifactTypes").getGet().getTags().remove("Artifact Type");

        document.getPaths().getItem("/admin/rules").getGet().getTags().remove("Global rules");
        document.getPaths().getItem("/admin/rules").getPost().getTags().remove("Global rules");
        document.getPaths().getItem("/admin/rules").getDelete().getTags().remove("Global rules");

        document.getPaths().getItem("/admin/rules/{rule}").getGet().getTags().remove("Global rules");
        document.getPaths().getItem("/admin/rules/{rule}").getPut().getTags().remove("Global rules");
        document.getPaths().getItem("/admin/rules/{rule}").getDelete().getTags().remove("Global rules");

        document.getPaths().getItem("/search/artifacts").getGet().getTags().remove("Artifacts");
        document.getPaths().getItem("/search/artifacts").getPost().getTags().remove("Artifacts");

        document.getTags().stream().filter(t -> !"Global rules".equals(t.getName()))
                .collect(Collectors.toList()).forEach(tag -> document.removeTag(tag));

        Extensible extensiblePaths = (Extensible) document.getPaths();
        extensiblePaths.removeExtension("x-codegen-contextRoot");

        // Now write out the modified document
        String outputDocumentString = Library.writeDocumentToJSONString(document);

        out.println("Writing modified document to: " + outputFile.getAbsolutePath());

        // Write the output to a file
        FileUtils.writeStringToFile(outputFile, outputDocumentString, StandardCharsets.UTF_8);
    }

    private static void attachHeaderSchema(OpenApiOperation operation, String info) {
        out.println("Adding explicit Content-Type header to " + info);
        var param = operation.createParameter();
        param.setName("Content-Type");
        param.setDescription(
                "This header is explicit so clients using the OpenAPI Generator are able select the content type. Ignore otherwise.");
        var schema = (OpenApi30Schema) param.createSchema();
        schema.setType("string");
        param.setSchema(schema);
        param.setIn("header");
        operation.addParameter(param);
    }
}
