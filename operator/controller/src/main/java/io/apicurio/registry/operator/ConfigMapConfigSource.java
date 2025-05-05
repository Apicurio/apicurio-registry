package io.apicurio.registry.operator;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static io.apicurio.registry.operator.resource.Labels.getMinimalOperatorSelectorLabels;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static io.fabric8.kubernetes.client.Config.autoConfigure;
import static java.lang.System.getenv;

public class ConfigMapConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(ConfigMapConfigSource.class);

    private final Properties properties = new Properties();

    private boolean initExecuted;

    private void init() {
        if (!initExecuted) {
            initExecuted = true;
            var namespace = getenv("POD_NAMESPACE");
            if (!isBlank(namespace)) {
                try (KubernetesClient client = new KubernetesClientBuilder()
                        .withConfig(new ConfigBuilder(autoConfigure(null)).withNamespace(namespace).build())
                        .build()) {
                    var configMaps = client.configMaps().withLabels(getMinimalOperatorSelectorLabels()).list();
                    if (configMaps.getItems().size() == 0) {
                        log.info("No operator ConfigMap found. You have to restart the operator pod if you create one.");
                    } else if (configMaps.getItems().size() == 1) {
                        var configMap = configMaps.getItems().get(0);
                        var config = configMap.getData().get("config.properties");
                        if (config != null) {
                            properties.load(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
                        } else {
                            log.warn("Operator ConfigMap found, but key \"config.properties\" is not present. You have to restart the operator pod if you update it.");
                        }
                        log.info("Operator ConfigMap found, loaded {} configuration options. You have to restart the operator pod if you update it.", properties.size());
                    } else {
                        log.error("Could not initialize ConfigMapConfigSource: Expected to find exactly one ConfigMap with labels {} in {}, but got: {}",
                                getMinimalOperatorSelectorLabels(),
                                namespace,
                                configMaps.getItems().stream().map(ResourceID::fromResource).toList());
                    }
                } catch (Exception ex) {
                    log.error("Could not initialize ConfigMapConfigSource.", ex);
                }
            } else {
                log.warn("Could not initialize ConfigMapConfigSource: Env. variable \"POD_NAMESPACE\" is not present or invalid.");
            }
        }
    }

    @Override
    public int getOrdinal() {
        init();
        // Lower than env. variables, but higher than application properties.
        return 275;
    }

    @Override
    public Set<String> getPropertyNames() {
        init();
        return properties.stringPropertyNames();
    }

    @Override
    public String getValue(String propertyName) {
        init();
        return properties.getProperty(propertyName);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
}
