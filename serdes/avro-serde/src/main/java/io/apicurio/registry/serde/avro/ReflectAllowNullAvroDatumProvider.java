package io.apicurio.registry.serde.avro;

import org.apache.avro.reflect.ReflectData;

public class ReflectAllowNullAvroDatumProvider<T> extends ReflectAvroDatumProvider<T> {

    public ReflectAllowNullAvroDatumProvider() {
        super(ReflectData.AllowNull.get());
    }

    public ReflectAllowNullAvroDatumProvider(Class<T> clazz) {
        super(ReflectData.AllowNull.get(),clazz);
    }
}
