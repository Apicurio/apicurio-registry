package io.apicurio.registry.utils.export;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class OptionsParser {

    private String url = null;
    private boolean inSecure = false;
    private String outputFile = "confluent-schema-registry-export.zip";
    private Map<String, Object> clientProps = new HashMap<>();

    public OptionsParser(String[] args) {
        if (args.length == 0) {
            return;
        }
        url = args[0];

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--insecure")) {
                inSecure = true;
            } else if (arg.equals("--output") || arg.equals("-o")) {
                if (i + 1 < args.length) {
                    outputFile = args[i + 1];
                    i++; // Skip the next argument since we consumed it
                }
            } else if (arg.equals("--client-props")) {
                String[] clientconf = Arrays.copyOfRange(args, i + 1, args.length);
                clientProps = Arrays.stream(clientconf).map(keyvalue -> keyvalue.split("="))
                        .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
                System.out.println("Parsed client properties " + clientProps);
                break;
            }
        }
    }

    public String getUrl() {
        return url;
    }

    public boolean isInSecure() {
        return inSecure;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public Map<String, Object> getClientProps() {
        return clientProps;
    }
}
