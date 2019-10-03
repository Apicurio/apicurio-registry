package io.apicurio.registry.utils.kafka;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * @author Ales Justin
 */
public class KafkaUtil {
    private static final Logger log = LoggerFactory.getLogger(KafkaUtil.class);

    public static void applyGroupId(String type, Properties properties) {
        String groupId = properties.getProperty("group.id");
        if (groupId == null) {
            log.warn("No group.id set for " + type + " properties, creating one ... DEV env only!!");
            properties.put("group.id", UUID.randomUUID().toString());
        }
    }

    public static Properties properties(KafkaProperties kp) {
        String prefix = (kp != null ? kp.value() : "");
        Config config = ConfigProvider.getConfig();
        Optional<String> po = config.getOptionalValue("quarkus.profile", String.class);
        if (po.isPresent()) {
            String profile = po.get();
            if (profile.length() > 0) {
                prefix = "%" + profile + "." + prefix;
            }
        }

        Properties properties = new Properties();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                properties.put(key.substring(prefix.length()), config.getValue(key, String.class));
            }
        }
        return properties;
    }

}
