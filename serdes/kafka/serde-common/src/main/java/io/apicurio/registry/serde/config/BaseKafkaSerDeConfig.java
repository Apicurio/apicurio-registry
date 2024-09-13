package io.apicurio.registry.serde.config;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.serde.config.KafkaSerdeConfig.ENABLE_HEADERS;
import static io.apicurio.registry.serde.config.KafkaSerdeConfig.ENABLE_HEADERS_DEFAULT;
import static io.apicurio.registry.serde.config.KafkaSerdeConfig.HEADERS_HANDLER;
import static io.apicurio.registry.serde.config.KafkaSerdeConfig.HEADERS_HANDLER_DEFAULT;
import static io.apicurio.registry.serde.config.SerdeConfig.*;
import static java.util.Map.entry;

public class BaseKafkaSerDeConfig extends SerdeConfig {

    public BaseKafkaSerDeConfig(Map<String, ?> originals) {
        this.originals = originals;
    }

    public BaseKafkaSerDeConfig() {
        this.originals = getDefaults();
    }

    public Object getIdHandler() {
        return this.getObject(ID_HANDLER);
    }

    public boolean enableHeaders() {
        return this.getBoolean(ENABLE_HEADERS);
    }

    public Object getHeadersHandler() {
        return this.getObject(HEADERS_HANDLER);
    }

    public IdOption useIdOption() {
        return IdOption.valueOf(this.getString(USE_ID));
    }

    @Override
    protected Map<String, ?> getDefaults() {
        Map<String, Object> joint = new HashMap<>(super.getDefaults());
        joint.putAll(DEFAULTS);
        return joint;
    }

    private static final Map<String, Object> DEFAULTS = Map.ofEntries(
            entry(ENABLE_HEADERS, ENABLE_HEADERS_DEFAULT), entry(HEADERS_HANDLER, HEADERS_HANDLER_DEFAULT));

}
