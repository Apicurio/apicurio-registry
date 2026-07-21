package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigProperties;
import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import org.jboss.logging.Logger;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine;

@ApplicationScoped
public class BrandedCommandLineProducer {

    private static final Logger log = Logger.getLogger(BrandedCommandLineProducer.class);
    private static final String PLACEHOLDER = "{{product-name}}";

    @Inject
    Config config;

    @Produces
    CommandLine createCommandLine(PicocliCommandLineFactory factory) {
        var cmd = factory.create();
        applyBranding(cmd.getCommandSpec(), resolveProductName());
        return cmd;
    }

    private String resolveProductName() {
        try {
            var name = config.getProperty(ConfigProperties.INTERNAL_BRANDING_PRODUCT_NAME);
            return name != null && !name.isEmpty() ? name : "Apicurio Registry CLI";
        } catch (Exception e) {
            log.debugf("Could not read branding config: %s", e.getMessage());
            return "Apicurio Registry CLI";
        }
    }

    private void applyBranding(CommandSpec spec, String productName) {
        var usage = spec.usageMessage();
        var desc = usage.description();
        boolean descChanged = false;
        for (int i = 0; i < desc.length; i++) {
            if (desc[i].contains(PLACEHOLDER)) {
                desc[i] = desc[i].replace(PLACEHOLDER, productName);
                descChanged = true;
            }
        }
        if (descChanged) {
            usage.description(desc);
        }
        var exitCodes = usage.exitCodeList();
        if (exitCodes != null && !exitCodes.isEmpty()) {
            var patched = new LinkedHashMap<String, String>();
            exitCodes.forEach((k, v) ->
                    patched.put(k, v.contains(PLACEHOLDER) ? v.replace(PLACEHOLDER, productName) : v));
            usage.exitCodeList(patched);
        }
        for (var sub : spec.subcommands().values()) {
            applyBranding(sub.getCommandSpec(), productName);
        }
    }
}
