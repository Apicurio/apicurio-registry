package io.apicurio.registry.iceberg.convert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal standalone command-line utility for converting schema files between Avro and Iceberg.
 *
 * Usage:
 * <pre>
 *   IcebergConvertCli avro2iceberg &lt;inputFile&gt; [outputFile]
 *   IcebergConvertCli iceberg2avro &lt;inputFile&gt; [outputFile]
 * </pre>
 * If no output file is given, the converted schema is written to standard output.
 */
public final class IcebergConvertCli {

    private static final Logger log = LoggerFactory.getLogger(IcebergConvertCli.class);

    private IcebergConvertCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            printUsageAndExit();
        }

        String direction = args[0];
        Path input = Path.of(args[1]);
        String source = Files.readString(input, StandardCharsets.UTF_8);

        String result;
        switch (direction) {
            case "avro2iceberg":
                result = IcebergSchemaConverter.avroToIceberg(source);
                break;
            case "iceberg2avro":
                result = IcebergSchemaConverter.icebergToAvro(source);
                break;
            default:
                printUsageAndExit();
                return;
        }

        if (args.length == 3) {
            Files.writeString(Path.of(args[2]), result, StandardCharsets.UTF_8);
        } else {
            System.out.println(result); // NOSONAR - this is the CLI's data output, not a log message
        }
    }

    private static void printUsageAndExit() {
        String usage = "Usage: IcebergConvertCli <avro2iceberg|iceberg2avro> <inputFile> [outputFile]";
        log.error(usage);
        throw new IllegalArgumentException(usage);
    }
}
