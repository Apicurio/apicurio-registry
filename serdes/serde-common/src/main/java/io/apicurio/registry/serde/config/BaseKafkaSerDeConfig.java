package io.apicurio.registry.serde.config;

import static io.apicurio.registry.serde.SerdeConfig.*;

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

public class BaseKafkaSerDeConfig extends AbstractConfig {

    private static ConfigDef buildConfigDef(ConfigDef base) {
        ConfigDef configDef = new ConfigDef(base)
                .define(ID_HANDLER, Type.CLASS, ID_HANDLER_DEFAULT, Importance.MEDIUM, "TODO docs")
                .define(ENABLE_CONFLUENT_ID_HANDLER, Type.BOOLEAN, false, Importance.LOW, "TODO docs")
                .define(ENABLE_HEADERS, Type.BOOLEAN, ENABLE_HEADERS_DEFAULT, Importance.MEDIUM, "TODO docs")
                .define(HEADERS_HANDLER, Type.CLASS, HEADERS_HANDLER_DEFAULT, Importance.MEDIUM, "TODO docs")
                .define(USE_ID, Type.STRING, USE_ID_DEFAULT, Importance.MEDIUM, "TODO docs");
        return configDef;
    }

    public BaseKafkaSerDeConfig(ConfigDef configDef, Map<?, ?> originals) {
        super(buildConfigDef(configDef), originals, false);
    }

    public BaseKafkaSerDeConfig(Map<?, ?> originals) {
        super(buildConfigDef(new ConfigDef()), originals, false);
    }

    public Object getIdHandler() {
        return this.get(ID_HANDLER);
    }

    public boolean enableConfluentIdHandler() {
        return this.getBoolean(ENABLE_CONFLUENT_ID_HANDLER);
    }

    public boolean enableHeaders() {
        return this.getBoolean(ENABLE_HEADERS);
    }

    public Object getHeadersHandler() {
        return this.get(HEADERS_HANDLER);
    }

    public IdOption useIdOption() {
        return IdOption.valueOf(this.getString(USE_ID));
    }

}
