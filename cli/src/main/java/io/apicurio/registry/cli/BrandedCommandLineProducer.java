package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.RuleUtil;
import io.apicurio.registry.cli.config.Config;
import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.jboss.logging.Logger;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine;

@ApplicationScoped
public class BrandedCommandLineProducer {

    private static final Logger log = Logger.getLogger(BrandedCommandLineProducer.class);
    private static final String PRODUCT_NAME = "{{product-name}}";
    private static final String RULE_TYPES = "{{rule-types}}";
    private static final String RULE_CONFIGS = "{{rule-configs}}";

    @Inject
    Config config;

    @Produces
    CommandLine createCommandLine(PicocliCommandLineFactory factory) {
        var cmd = factory.create();
        applyPlaceholders(cmd.getCommandSpec(), resolveProductName());
        return cmd;
    }

    private String resolveProductName() {
        try {
            var name = config.read().getConfig().get("internal.branding.product-name");
            return name != null && !name.isEmpty() ? name : "Apicurio Registry CLI";
        } catch (Exception e) {
            log.debugf("Could not read branding config: %s", e.getMessage());
            return "Apicurio Registry CLI";
        }
    }

    // Recursively resolves help-text placeholders in the command description, its options, and its
    // positional parameters, then descends into the subcommand tree.
    private void applyPlaceholders(CommandSpec spec, String productName) {
        var usage = spec.usageMessage();
        var desc = resolve(usage.description(), productName);
        if (desc != null) {
            usage.description(desc);
        }
        var exitCodes = usage.exitCodeList();
        if (exitCodes != null && !exitCodes.isEmpty()) {
            var patched = new LinkedHashMap<String, String>();
            exitCodes.forEach((k, v) -> patched.put(k, resolve(v, productName)));
            usage.exitCodeList(patched);
        }
        // Options and positional parameters are immutable once built, so rebuild any whose
        // description changed and swap it back into the command spec.
        for (var option : List.copyOf(spec.options())) {
            var resolved = resolve(option.description(), productName);
            if (!Arrays.equals(resolved, option.description())) {
                spec.remove(option);
                spec.add(option.toBuilder().description(resolved).build());
            }
        }
        for (var positional : List.copyOf(spec.positionalParameters())) {
            var resolved = resolve(positional.description(), productName);
            if (!Arrays.equals(resolved, positional.description())) {
                spec.remove(positional);
                spec.add(positional.toBuilder().description(resolved).build());
            }
        }
        for (var sub : spec.subcommands().values()) {
            applyPlaceholders(sub.getCommandSpec(), productName);
        }
    }

    private String[] resolve(String[] lines, String productName) {
        if (lines == null) {
            return null;
        }
        var resolved = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            resolved[i] = resolve(lines[i], productName);
        }
        return resolved;
    }

    private String resolve(String text, String productName) {
        if (text == null) {
            return null;
        }
        var resolved = text;
        if (resolved.contains(PRODUCT_NAME)) {
            resolved = resolved.replace(PRODUCT_NAME, productName);
        }
        if (resolved.contains(RULE_TYPES)) {
            resolved = resolved.replace(RULE_TYPES, RuleUtil.renderRuleTypes());
        }
        if (resolved.contains(RULE_CONFIGS)) {
            resolved = resolved.replace(RULE_CONFIGS, RuleUtil.renderRuleConfigs());
        }
        return resolved;
    }
}
